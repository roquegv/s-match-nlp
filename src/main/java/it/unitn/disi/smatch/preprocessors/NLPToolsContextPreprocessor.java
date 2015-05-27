package it.unitn.disi.smatch.preprocessors;

import it.unitn.disi.nlptools.ILabelPipeline;
import it.unitn.disi.nlptools.components.PipelineComponentException;
import it.unitn.disi.nlptools.data.ILabel;
import it.unitn.disi.nlptools.data.IMultiWord;
import it.unitn.disi.nlptools.data.IToken;
import it.unitn.disi.nlptools.data.Label;
import it.unitn.disi.nlptools.data.MultiWord;
import it.unitn.disi.nlptools.data.Token;
import it.unitn.disi.smatch.async.AsyncTask;
import it.unitn.disi.smatch.data.ling.IAtomicConceptOfLabel;
import it.unitn.disi.smatch.data.trees.IContext;
import it.unitn.disi.smatch.data.trees.INode;
import it.unitn.disi.smatch.oracles.ILinguisticOracle;
import it.unitn.disi.sweb.core.nlp.IOpenPipeline;
import it.unitn.disi.sweb.core.nlp.model.NLMultiWord;
import it.unitn.disi.sweb.core.nlp.model.NLSentence;
import it.unitn.disi.sweb.core.nlp.model.NLText;
import it.unitn.disi.sweb.core.nlp.model.NLToken;
import it.unitn.disi.sweb.core.nlp.parameters.NLPParameters;
import it.unitn.disi.sweb.core.nlp.pipelines.SemanticMatchingPipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.*;


/**
 * Performs linguistic preprocessing using NLPTools, on errors falls back to heuristic-based one.
 *
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class NLPToolsContextPreprocessor extends BaseContextPreprocessor implements IAsyncContextPreprocessor {

    private static final Logger log = LoggerFactory.getLogger(NLPToolsContextPreprocessor.class);

//    private final ILabelPipeline pipeline;
    @Autowired
    @Qualifier("NLPParameters")
    private NLPParameters parameters;

    @Autowired
    @Qualifier("SemanticMatchingPipeline")
    private IOpenPipeline<NLPParameters> pipeline;
    
    private final DefaultContextPreprocessor dcp;

    private int fallbackCount;
    
//    private static ClassPathXmlApplicationContext ctx;
//    static{//is this the right place?
//    	ctx = new ClassPathXmlApplicationContext("classpath:/META-INF/smatch-context.xml");
//        pipeline = ctx.getBean(NLPToolsContextPreprocessor.class);
//    }
    
    public NLPToolsContextPreprocessor(ILinguisticOracle linguisticOracle) {
        super(linguisticOracle);
        this.dcp = null;
    }

    public NLPToolsContextPreprocessor(IOpenPipeline<NLPParameters> pipeline, ILinguisticOracle linguisticOracle) {
        super(linguisticOracle);
        this.pipeline = pipeline;
        this.dcp = null;
    }

    public NLPToolsContextPreprocessor(IOpenPipeline<NLPParameters> pipeline, IContext context, ILinguisticOracle linguisticOracle) {
        super(context, linguisticOracle);
        this.pipeline = pipeline;
        this.dcp = null;
    }

    public NLPToolsContextPreprocessor(IOpenPipeline<NLPParameters> pipeline, DefaultContextPreprocessor dcp, ILinguisticOracle linguisticOracle) {
        super(null, linguisticOracle);
        this.pipeline = pipeline;
        this.dcp = dcp;
    }

    public NLPToolsContextPreprocessor(IOpenPipeline<NLPParameters> pipeline, DefaultContextPreprocessor dcp, IContext context, ILinguisticOracle linguisticOracle) {
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
//        try {
//            pipeline.beforeProcessing();
//        } catch (PipelineComponentException e) {
//            throw new ContextPreprocessorException(e.getMessage(), e);
//        }

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

//        try {
//            pipeline.afterProcessing();
//        } catch (PipelineComponentException e) {
//            throw new ContextPreprocessorException(e.getMessage(), e);
//        }
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
//        result.setContext(pathToRootPhrases);
        
        //Convert ILabel to NLText
        NLText resultLabel = new NLText(label,currentNode.getLanguage());
        resultLabel.addSentence(new NLSentence(label));
        List<NLSentence> context = new ArrayList<NLSentence>();
        for (ILabel phrase: pathToRootPhrases){
        	context.add(new NLSentence(phrase.getText()));
        }
        parameters.setContext(context);
        try {
//            result.setLanguage(currentNode.getLanguage());
//            pipeline.process(result);
        	pipeline.runPipeline(resultLabel, parameters);
        	//Convert NLText to Label
        	result = nlTextToLabel(resultLabel,pathToRootPhrases);

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
            formula = formula.replaceAll("(\\d+)", currentNode.nodeData().getId() + "_$1");
            formula = formula.trim();
            //set it to the node
            currentNode.nodeData().setLabelFormula(formula);
        } catch (Exception e) {//TODO what kind of exception could throw SCROLL pipeline?  
            if (log.isWarnEnabled()) {
                log.warn("Falling back to heuristic parser for label (" + result.getText() + "): " + e.getMessage(), e);
                fallbackCount++;
                dcp.processNode(currentNode, new HashSet<String>());
            }
        }
        return result;
    }
    
    private static ILabel nlTextToLabel(NLText text,List<ILabel> context){
    	ILabel converted = new Label(text.getText());
    	//set context
    	converted.setContext(context);
    	//set tokens
    	List<IToken> tokens = new ArrayList<IToken>();
    	for (NLSentence sent: text.getSentences()){//there is only one sentence per label
    		for (NLToken token: sent.getTokens()){
    			tokens.add(new Token(token.getText()));
    		}
    	}
    	converted.setTokens(tokens);
    	//set multiwords
    	List<IMultiWord> multiWords = new ArrayList<IMultiWord>();
    	for (NLSentence sent: text.getSentences()){//there is only one sentence per label
    		for (NLMultiWord multiWord: sent.getMultiWords()){
    			multiWords.add(new MultiWord(multiWord.getText()));
    		}
    	}
    	converted.setMultiWords(multiWords);
    	//set formula
    	converted.setFormula("0");//TODO this is not yet implemented in SCROLL
    	//set language
    	converted.setLanguage(text.getLanguage());
    	return converted;
    }
    
}