/*******************************************************************************
  * OscaR is free software: you can redistribute it and/or modify
  * it under the terms of the GNU Lesser General Public License as published by
  * the Free Software Foundation, either version 2.1 of the License, or
  * (at your option) any later version.
  *
  * OscaR is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU Lesser General Public License  for more details.
  *
  * You should have received a copy of the GNU Lesser General Public License along with OscaR.
  * If not, see http://www.gnu.org/licenses/lgpl-3.0.en.html
  ******************************************************************************/
/**
 * @author Jean-Noel Monette
 */
package fzn.parser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.PredictionMode;
import fzn.Model;
import minicpbp.util.exception.NotImplementedException;
import minicpbp.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;


public class FZParser {
	public static Model readFlatZincModelFromFile(String fname, boolean acceptAnyCstr){
		try{
		//ANTLRInputStream input = new ANTLRInputStream(new FileInputStream(fname));
		Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(fname), StandardCharsets.UTF_8));
		UnbufferedCharStream input = new UnbufferedCharStream(new CommentStrippingReader(reader));
		return readFlatZincModel(input, acceptAnyCstr);
		}catch(IOException e){
	        Log.error("File " + fname + " not found. Aborting.");
			System.exit(1);
		}
		return null;
	}
	public static Model readFlatZincModelFromString(String content, boolean acceptAnyCstr){
		UnbufferedCharStream input = new UnbufferedCharStream(new CommentStrippingReader(new StringReader(content)));
		return readFlatZincModel(input, acceptAnyCstr);
	}
	public static Model readFlatZincModel(CharStream input, boolean acceptAnyCstr){
		Model m = new Model(acceptAnyCstr);
		//try{
	        FlatzincLexer lex = new FlatzincLexer(input);
			//CommonTokenStream tokens = new CommonTokenStream(lex);
			lex.setTokenFactory(new CommonTokenFactory(true));
			TokenStream tokens = new UnbufferedTokenStream<CommonToken>(lex);
			FlatzincParser p = new FlatzincParser(tokens, m);
			p.setBuildParseTree(false);//in order to get acceptable performance on large files
			//Handling errors
			p.removeParseListeners();
			p.removeErrorListeners();
			//Handling errors
			//p.addErrorListener(new DiagnosticErrorListener());
			p.addErrorListener(new BaseErrorListener() {
              public void syntaxError(Recognizer<?, ?> recon, Object offendingSymbol, int line,
                  int positionInLine, String message, RecognitionException e) { 
            	  //System.out.println(offendingSymbol);
                throw new NotImplementedException("line "+line+":"+positionInLine+" "+message);
              }
              
            });
			//The following try/catch and prediction modes implement this: https://theantlrguy.atlassian.net/wiki/pages/viewpage.action?pageId=1900591
			p.getInterpreter().setPredictionMode(PredictionMode.SLL);
			
			//for debugging?
			//p.addErrorListener(new DiagnosticErrorListener());
			//p.getInterpreter().setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION);

	        try{
	        	p.flatzinc_model();
	        }catch (Exception e){
	        	e.printStackTrace();
	        	//log.apply(0,"Simple SLL Parsing Failed. Fallback to complete LL parsing");
	        	//Add to remove the following line for being able to use unbuffered streams!
	        	//tokens.reset();
	        	p.reset();
	        	p.getInterpreter().setPredictionMode(PredictionMode.LL);    
	        	p.flatzinc_model();  
	        }
	        if(p.getNumberOfSyntaxErrors()>0){
	          
	          throw new NotImplementedException("Parsing Error Somewhere");
	          /*Log.error("Syntax error. Aborting.");
	          Log.error("If the flatzinc file is correct, please report to the developers.");
	          System.exit(1);*/
	        }
			return m;
		/*}catch(RecognitionException e){
			e.printStackTrace();//TODO: report more friendly messages
			Log.error("Syntax error. Aborting.");
	          Log.error("If the flatzinc file is correct, please report to the developers.");
			System.exit(1);
		}catch(ParsingException e){
		  Log.error(e.getMessage());
		  //e.printStackTrace();
		  //TODO: report more friendly and complete messages
          Log.error("Syntax error. Aborting.");
          Log.error("If the flatzinc file is correct, please report to the developers.");
          System.exit(1);
		}*/
		//return null;
	}

	/**
	 * Drops FlatZinc comments ('%' to end of line, outside string literals).
	 * The generated lexer has no comment rule, and MiniZinc writes a comment
	 * header into every .fzn file it produces.
	 */
	static final class CommentStrippingReader extends FilterReader {
		private boolean inString = false;
		private boolean escaped = false;
		private boolean inComment = false;

		CommentStrippingReader(Reader in) {
			super(in);
		}

		@Override
		public int read() throws IOException {
			while (true) {
				int c = in.read();
				if (c == -1)
					return -1;
				if (inComment) {
					if (c != '\n')
						continue;
					inComment = false;
				} else if (inString) {
					if (escaped)
						escaped = false;
					else if (c == '\\')
						escaped = true;
					else if (c == '"')
						inString = false;
				} else if (c == '"') {
					inString = true;
				} else if (c == '%') {
					inComment = true;
					continue;
				}
				return c;
			}
		}

		@Override
		public int read(char[] buf, int off, int len) throws IOException {
			int n = 0;
			while (n < len) {
				int c = read();
				if (c == -1)
					return n == 0 ? -1 : n;
				buf[off + n++] = (char) c;
			}
			return n;
		}

		@Override
		public boolean markSupported() {
			return false;
		}
	}
}
