package it.unitn.disi.nlptools.components.lemmatizers;

import it.unitn.disi.nlptools.components.PipelineComponentException;
import it.unitn.disi.nlptools.data.ILabel;
import it.unitn.disi.nlptools.data.IToken;
import it.unitn.disi.nlptools.pipelines.LabelPipelineComponent;
import it.unitn.disi.smatch.oracles.ILinguisticOracle;
import it.unitn.disi.smatch.oracles.LinguisticOracleException;

import java.util.List;

/**
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class LinguisticOracleLemmatizer extends LabelPipelineComponent {

    private final ILinguisticOracle oracle;

    public LinguisticOracleLemmatizer(ILinguisticOracle oracle) {
        this.oracle = oracle;
    }

    public void process(ILabel instance) throws PipelineComponentException {
        for (IToken token : instance.getTokens()) {
            try {
                List<String> lemmas = oracle.getBaseForms(token.getText(), instance.getLanguage());
                if (0 < lemmas.size()) {
                    token.setLemma(lemmas.get(0));//lemma is supposed to be that of an "active" sense
                }
            } catch (LinguisticOracleException e) {
                throw new PipelineComponentException(e.getMessage(), e);
            }
        }
    }
}
