package it.unitn.disi.smatch.preprocessors;

import it.unitn.disi.nlptools.ILabelPipeline;
import it.unitn.disi.nlptools.components.PipelineComponentException;
import it.unitn.disi.nlptools.data.ILabel;
import it.unitn.disi.nlptools.data.IToken;
import it.unitn.disi.nlptools.data.Label;
import it.unitn.disi.smatch.async.AsyncTask;
import it.unitn.disi.smatch.data.ling.IAtomicConceptOfLabel;
import it.unitn.disi.smatch.data.trees.IContext;
import it.unitn.disi.smatch.data.trees.INode;
import it.unitn.disi.smatch.oracles.ILinguisticOracle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


/**
 * Performs linguistic preprocessing using NLPTools, on errors falls back to heuristic-based one.
 *
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class NLPToolsContextPreprocessor extends BaseContextPreprocessor implements IAsyncContextPreprocessor {

    private static final Logger log = LoggerFactory.getLogger(NLPToolsContextPreprocessor.class);

    private final ILabelPipeline pipeline;
    private final DefaultContextPreprocessor dcp;

    private int fallbackCount;

    public NLPToolsContextPreprocessor(ILabelPipeline pipeline, ILinguisticOracle linguisticOracle) {
        super(linguisticOracle);
        this.pipeline = pipeline;
        this.dcp = null;
    }

    public NLPToolsContextPreprocessor(ILabelPipeline pipeline, IContext context, ILinguisticOracle linguisticOracle) {
        super(context, linguisticOracle);
        this.pipeline = pipeline;
        this.dcp = null;
    }

    public NLPToolsContextPreprocessor(ILabelPipeline pipeline, DefaultContextPreprocessor dcp, ILinguisticOracle linguisticOracle) {
        super(null, linguisticOracle);
        this.pipeline = pipeline;
        this.dcp = dcp;
    }

    public NLPToolsContextPreprocessor(ILabelPipeline pipeline, DefaultContextPreprocessor dcp, IContext context, ILinguisticOracle linguisticOracle) {
        super(context, linguisticOracle);
        this.pipeline = pipeline;
        this.dcp = dcp;
    }

    public void preprocess(IContext context) throws ContextPreprocessorException {
        //go DFS, processing label-by-label, keeping path-to-root as context
        //process each text getting the formula
        fallbackCount = 0;
        // TODO: this is probably not the best place for the language detection
        // code, should maybe create a processing component for it
        String language = linguisticOracle.detectLanguage(context);
        linguisticOracle.readMultiwords(language);
        context.setLanguage(language);
        try {
            pipeline.beforeProcessing();
        } catch (PipelineComponentException e) {
            throw new ContextPreprocessorException(e.getMessage(), e);
        }

        List<INode> queue = new ArrayList<>();
        List<INode> pathToRoot = new ArrayList<>();
        List<ILabel> pathToRootPhrases = new ArrayList<>();
        queue.add(context.getRoot());

        while (!queue.isEmpty()) {
            INode currentNode = queue.remove(0);

            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            if (null == currentNode) {
                pathToRoot.remove(pathToRoot.size() - 1);
                pathToRootPhrases.remove(pathToRootPhrases.size() - 1);
            } else {
                ILabel currentPhrase;
                currentNode.setLanguage(context.getLanguage());
                currentPhrase = processNode(currentNode, pathToRootPhrases);

                progress();

                List<INode> children = currentNode.getChildren();
                if (0 < children.size()) {
                    queue.add(0, null);
                    pathToRoot.add(currentNode);
                    pathToRootPhrases.add(currentPhrase);
                }
                for (int i = children.size() - 1; i >= 0; i--) {
                    queue.add(0, children.get(i));
                }

            }
        }

        try {
            pipeline.afterProcessing();
        } catch (PipelineComponentException e) {
            throw new ContextPreprocessorException(e.getMessage(), e);
        }
        log.info("Processed nodes: " + getProgress() + ", fallbacks: " + fallbackCount);
    }

    @Override
    public AsyncTask<Void, INode> asyncPreprocess(IContext context) {
        return new NLPToolsContextPreprocessor(pipeline, dcp, context, linguisticOracle);
    }

    /**
     * Converts current node label into a formula using path to root as a context
     *
     * @param currentNode       a node to process
     * @param pathToRootPhrases phrases in the path to root
     * @return phrase instance for a current node label
     * @throws ContextPreprocessorException ContextPreprocessorException
     */
    private ILabel processNode(INode currentNode, List<ILabel> pathToRootPhrases) throws ContextPreprocessorException {
        log.trace("preprocessing node: " + currentNode.nodeData().getId() + ", label: " + currentNode.nodeData().getName());

        // reset old preprocessing
        currentNode.nodeData().setLabelFormula("");
        currentNode.nodeData().setNodeFormula("");
        currentNode.nodeData().getConcepts().clear();

        String label = currentNode.nodeData().getName();
        ILabel result = new Label(label);
        result.setContext(pathToRootPhrases);
        try {
            result.setLanguage(currentNode.getLanguage());
            pipeline.process(result);

            //should contain only token indexes. including not recognized, but except closed class tokens.
            //something like
            // 1 & 2
            // 1 & (3 | 4)
            String formula = result.getFormula();
            currentNode.nodeData().setIsPreprocessed(true);

            //create concepts. one acol for each concept (meaningful) token
            //non-concept tokens should not make it up to a formula.
            String[] tokenIndexes = formula.split("[ ()&|~]");
            Set<String> indexes = new HashSet<>(Arrays.asList(tokenIndexes));
            List<IToken> tokens = result.getTokens();
            for (int i = 0; i < tokens.size(); i++) {
                IToken token = tokens.get(i);
                String tokenIdx = Integer.toString(i);
                if (indexes.contains(tokenIdx)) {
                    IAtomicConceptOfLabel acol = currentNode.nodeData().createConcept();
                    acol.setId(i);
                    acol.setToken(token.getText());
                    acol.setLemma(token.getLemma());
                    acol.setSenses(token.getSenses());
                    currentNode.nodeData().getConcepts().add(acol);
                }
            }

            //prepend all token references with node id
            formula = formula.replaceAll("(\\d+)", currentNode.nodeData().getId() + ".$1");
            formula = formula.trim();
            //set it to the node
            currentNode.nodeData().setLabelFormula(formula);
        } catch (PipelineComponentException e) {
            if (log.isWarnEnabled()) {
                log.warn("Falling back to heuristic parser for label (" + result.getText() + "): " + e.getMessage(), e);
                fallbackCount++;
                dcp.processNode(currentNode, new HashSet<String>());
            }
        }
        return result;
    }
}