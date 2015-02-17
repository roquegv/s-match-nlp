package it.unitn.disi.nlptools.components.mwrecognizers;

import it.unitn.disi.nlptools.components.PipelineComponentException;
import it.unitn.disi.nlptools.data.ILabel;
import it.unitn.disi.nlptools.data.IMultiWord;
import it.unitn.disi.nlptools.data.IToken;
import it.unitn.disi.nlptools.data.MultiWord;
import it.unitn.disi.nlptools.pipelines.LabelPipelineComponent;
import it.unitn.disi.smatch.oracles.ILinguisticOracle;
import it.unitn.disi.smatch.oracles.LinguisticOracleException;

import java.util.*;

/**
 * Recognizes multiwords within consecutive tokens. Given:
 * [a] [cappella] [and] [gospel] [singing], it finds two multiwords {0,1} and {3,4}.
 * <p/>
 * Optionally joins multiwords, replacing original tokens with multiwords.
 *
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class SimpleMultiwordRecognizer extends LabelPipelineComponent {

    private final ILinguisticOracle oracle;
    private final boolean joinTokens;

    public SimpleMultiwordRecognizer(ILinguisticOracle oracle) {
        this(oracle, true);
    }

    public SimpleMultiwordRecognizer(ILinguisticOracle oracle, boolean joinTokens) {
        this.oracle = oracle;
        this.joinTokens = joinTokens;
    }

    private static final Comparator<IMultiWord> mwComparator = new Comparator<IMultiWord>() {
        public int compare(IMultiWord o1, IMultiWord o2) {
            return o2.getTokens().size() - o1.getTokens().size();
        }
    };

    public void process(ILabel instance) throws PipelineComponentException {
        for (int i = 0; i < instance.getTokens().size(); i++) {
            IToken token = instance.getTokens().get(i);
            ArrayList<ArrayList<String>> entries;
            try {
                entries = oracle.getMultiwords(token.getLemma().toLowerCase(), instance.getLanguage());
            } catch (LinguisticOracleException e) {
                throw new PipelineComponentException(e.getMessage(), e);
            }
            if (null != entries && 0 < entries.size()) {
                for (List<String> entry : entries) {
                    int mwIdx = 0;
                    while (mwIdx < entry.size() && (mwIdx < (instance.getTokens().size() - i))) {
                        if (instance.getTokens().get(i + mwIdx).getText().equalsIgnoreCase(entry.get(mwIdx)) ||
                                //last token can be in plural
                                (entry.size() - 1 == mwIdx && instance.getTokens().get(i + mwIdx).getLemma().equalsIgnoreCase(entry.get(mwIdx)))
                                ) {
                            mwIdx++;
                        } else {
                            break;
                        }
                    }
                    if (mwIdx == entry.size()) {
                        StringBuilder b = new StringBuilder();
                        for (String piece : entry) {
                            b.append(piece).append(' ');
                        }
                        MultiWord mw = new MultiWord(b.substring(0, b.length() - 1));
                        ArrayList<Integer> indexes = new ArrayList<>();
                        ArrayList<IToken> tokens = new ArrayList<>();
                        for (int idx = 0; idx < entry.size(); idx++) {
                            indexes.add(i + idx);
                            tokens.add(instance.getTokens().get(i + idx));
                        }
                        mw.setTokenIndexes(indexes);
                        mw.setTokens(tokens);
                        mw.setLemma(mw.getText());
                        mw.setPOSTag(instance.getTokens().get(i + mwIdx - 1).getPOSTag());
                        if (0 == instance.getMultiWords().size()) {
                            instance.setMultiWords(new ArrayList<IMultiWord>(Arrays.asList(mw)));
                        } else {
                            instance.getMultiWords().add(mw);
                        }
                    }
                }//for entries
            }//if entries
        }

        if (joinTokens) {
            //start from longest ones, to handle cases like "adult male", "adult male body"
            List<IMultiWord> mws = new ArrayList<>(instance.getMultiWords());
            Collections.sort(mws, mwComparator);
            for (IMultiWord multiWord : mws) {
                int idx = instance.getTokens().indexOf(multiWord.getTokens().get(0));
                if (-1 < idx) {
                    for (IToken token : multiWord.getTokens()) {
                        instance.getTokens().remove(token);
                    }
                    instance.getTokens().add(idx, multiWord);
                }
            }
        }
    }
}