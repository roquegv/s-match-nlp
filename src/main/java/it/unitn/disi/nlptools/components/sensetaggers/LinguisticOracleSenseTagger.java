package it.unitn.disi.nlptools.components.sensetaggers;

import it.unitn.disi.nlptools.components.PipelineComponentException;
import it.unitn.disi.nlptools.data.ILabel;
import it.unitn.disi.nlptools.data.IToken;
import it.unitn.disi.nlptools.pipelines.LabelPipelineComponent;
import it.unitn.disi.smatch.data.ling.ISense;
import it.unitn.disi.smatch.oracles.ILinguisticOracle;
import it.unitn.disi.smatch.oracles.LinguisticOracleException;

import java.util.List;

/**
 * Tags senses using linguistic oracle.
 *
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class LinguisticOracleSenseTagger extends LabelPipelineComponent {

    private final ILinguisticOracle oracle;

    public LinguisticOracleSenseTagger(ILinguisticOracle oracle) {
        this.oracle = oracle;
    }

    public void process(ILabel instance) throws PipelineComponentException {
        tagSenses(instance.getTokens(), instance.getLanguage());
    }

    private void tagSenses(List<? extends IToken> tokens, String language) throws PipelineComponentException {
        for (IToken token : tokens) {
            try {
                List<ISense> senses = oracle.getSenses(token.getText(), language);
                if (0 < senses.size()) {//to save memory with default empty lists already there
                    token.setSenses(senses);
                }
            } catch (LinguisticOracleException e) {
                throw new PipelineComponentException(e.getMessage(), e);
            }
        }
    }
}