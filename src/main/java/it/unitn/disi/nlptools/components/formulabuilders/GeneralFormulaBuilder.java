package it.unitn.disi.nlptools.components.formulabuilders;

import it.unitn.disi.nlptools.components.PipelineComponentException;
import it.unitn.disi.nlptools.data.ILabel;
import it.unitn.disi.nlptools.pipelines.ILabelPipelineComponent;
import it.unitn.disi.nlptools.pipelines.LabelPipelineComponent;
import it.unitn.disi.sweb.core.nlp.components.connectordetector.LCDetector;
import it.unitn.disi.sweb.core.nlp.model.NLParseTree;
import it.unitn.disi.sweb.core.nlp.model.NLSentence;
import it.unitn.disi.sweb.core.nlp.model.NLToken;
import opennlp.tools.parser.Parse;

public class GeneralFormulaBuilder extends LabelPipelineComponent implements ILabelPipelineComponent{

	@Override
	public void process(ILabel instance) throws PipelineComponentException {
		String formula = buildFormula(instance.getParseTree());
		instance.setFormula(formula);
	}
	
	public String buildFormula(NLParseTree tree){
		if (tree.isLeaf()){
			return getConnector(tree);
		}else{
			StringBuilder formula = new StringBuilder();
			for (NLParseTree subtree : tree.getChildren()){
				String subformula = buildFormula(subtree);
				join(formula, subformula);
			}
			bracket(formula);
			return formula.toString();
		}
	}
	
	public static void join(StringBuilder fullformula, String subformula){
		if (subformula == null || subformula.isEmpty())
			return;
		if (LCDetector.CONJUNCTION_SYMBOL.equals(subformula)||
			LCDetector.DISJUNCTION_SYMBOL.equals(subformula)||
			LCDetector.NEGATION_SYMBOL.equals(subformula)||
			fullformula.toString().endsWith(LCDetector.CONJUNCTION_SYMBOL)||
			fullformula.toString().endsWith(LCDetector.DISJUNCTION_SYMBOL)||
			fullformula.toString().endsWith(LCDetector.NEGATION_SYMBOL)||
			fullformula.toString().isEmpty()){
			fullformula.append(subformula);
		}else{
			fullformula.append(LCDetector.CONJUNCTION_SYMBOL).append(subformula);
		}
	}
	
	public static void bracket(StringBuilder formula){
		if (LCDetector.DISJUNCTION_SYMBOL.equals(formula.toString()))
			return;
		int balancedBrackets = 0;
		boolean needsBrackets = false;
		for (int i = 0; i < formula.length(); i++){
			char c = formula.charAt(i);
			if (c == '('){
				balancedBrackets++;
			}else if (c == ')'){
				balancedBrackets--;
			}else if (c == '|' && balancedBrackets == 0){
				needsBrackets = true;
				break;
			}
		}
		if (needsBrackets){
			formula.insert(0, '(').append(')');
		}
	}
	
	public static String getConnector(NLParseTree tree){
		NLToken token = tree.getToken();
		if (token == null) return "";
		if ("OTHER".equals(token.getPos())){
			if (token.getProp(LCDetector.NAMESPACE, LCDetector.ATTR) == null)
				return "";
			else
				return (String) token.getProp(LCDetector.NAMESPACE, LCDetector.ATTR);
		}else{
			return Integer.toString(tree.getFirstTokenPosition());
		}
	}
	
	public static void main(String[] argv){
		String tree = "(TOP (SENTENCE (SN (GRUP.NOM (NCFS000 Historia) (SP (PREP (SPS00 de)) (SN (SPEC (DA0FS0 la)) (GRUP.NOM (NCFS000 filosofía)))))) (GRUP.VERB (CC y)) (NCFS000 ciencia)))";
		String text = "Historia de la filosofía y ciencia";
//		NLText text = new NLText(text);
		NLSentence sentence = new NLSentence(text);
		String[] splitted = text.split(" ");
		String[] postags = {"NOUN","OTHER","OTHER","NOUN","OTHER","NOUN"};
		String[] connectors = {null,null,null,null,LCDetector.DISJUNCTION_SYMBOL,null};
		NLToken token;
		
		for (int i = 0; i < splitted.length; i++){
			token = new NLToken(splitted[i]);
			token.setPos(postags[i]);
			token.setProp(LCDetector.NAMESPACE, LCDetector.ATTR, connectors[i]);
			sentence.addToken(token);
		}
		
		NLParseTree parseTree = NLParseTree.deserializeTree(tree, sentence);
		GeneralFormulaBuilder gfb = new GeneralFormulaBuilder();
//		StringBuilder formula = new StringBuilder("");
		String formula;
		formula = gfb.buildFormula(parseTree);
		System.out.println("FORMULA: "+formula);
	}

}
