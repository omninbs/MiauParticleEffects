package org.miau.particleeffects.exparticle.expr;

import java.util.ArrayList;
import java.util.List;

public class Parser {
   public Parser() {
   }

   public static Expression[] parseBlock(Lexer lexer) {
      List<Expression> exps = new ArrayList<>();

      while (!isEnd(lexer.peekToken())) {
         Expression exp = parseExp(lexer);
         exps.add(exp);
         if (lexer.peekToken().enumToken == EnumToken.SEMI) {
            lexer.nextToken();
         }
      }

      return exps.toArray(new Expression[exps.size()]);
   }

   private static Expression parseAssignOrFunctionCallExp(Lexer lexer) {
      try {
         lexer.snapshot();
         Expression exp = parseAssignExp(lexer);
         lexer.popSnapshot();
         return exp;
      } catch (RuntimeException var3) {
         lexer.recovery();
         if (lexer.peekToken().enumToken == EnumToken.LPAREN) {
            return parseParenExp(lexer);
         } else {
            lexer.snapshot();
            lexer.nextIdentifier();
            Token token = lexer.peekToken();
            lexer.recovery();
            switch (token.enumToken) {
               case LPAREN:
                  return parseFunctionCallExp(lexer);
               default:
                  return parseIdentifier(lexer);
            }
         }
      }
   }

   private static Expression parseAssignExp(Lexer lexer) {
      Expression[] vars = parseVars(lexer);
      lexer.nextToken(EnumToken.ASSIGN);
      Expression[] exps = parseExps(lexer);
      if (vars.length != exps.length) {
         throw new RuntimeException("assign expression's vars and exps length not equals");
      } else {
         return new Expression.AssignExp(lexer.line(), vars, exps);
      }
   }

   private static Expression[] parseVars(Lexer lexer) {
      List<Expression> vars = new ArrayList<>();
      vars.add(parseVar(lexer));

      while (lexer.peekToken().enumToken == EnumToken.COMMA) {
         lexer.nextToken();
         vars.add(parseVar(lexer));
      }

      return vars.toArray(new Expression[vars.size()]);
   }

   private static Expression parseVar(Lexer lexer) {
      switch (lexer.peekToken().enumToken) {
         case LPAREN:
            return parseVarMatrix(lexer);
         case IDENTIFIER:
            return parseIdentifier(lexer);
         default:
            throw new RuntimeException("not a var");
      }
   }

   private static Expression parseVarMatrix(Lexer lexer) {
      Expression exp = parseParenExp(lexer);
      if (!(exp instanceof Expression.NameMatrixExp)) {
         throw new RuntimeException("not a var matrix");
      } else {
         return exp;
      }
   }

   private static Expression[] parseExps(Lexer lexer) {
      List<Expression> exps = new ArrayList<>();
      exps.add(parseExp(lexer));

      while (lexer.peekToken().enumToken == EnumToken.COMMA) {
         lexer.nextToken();
         exps.add(parseExp(lexer));
      }

      return exps.toArray(new Expression[exps.size()]);
   }

   private static Expression parseExp(Lexer lexer) {
      return parseExp7(lexer);
   }

   private static Expression parseIdentifier(Lexer lexer) {
      Token token = lexer.nextIdentifier();
      return new Expression.NameExp(token.line, token.token);
   }

   private static Expression parseFunctionCallExp(Lexer lexer) {
      Token name = lexer.nextIdentifier();
      int line = lexer.nextToken(EnumToken.LPAREN).line;
      Expression[] exps;
      if (lexer.peekToken().enumToken != EnumToken.RPAREN) {
         exps = parseExps(lexer);
      } else {
         exps = new Expression[0];
      }

      int lastLine = lexer.nextToken(EnumToken.RPAREN).line;
      return new Expression.FunctionCallExp(line, lastLine, name.token, exps);
   }

   private static Expression parseParenExp(Lexer lexer) {
      int line = lexer.nextToken(EnumToken.LPAREN).line;
      List<Expression[]> exps = new ArrayList<>();
      Expression[] expRow = parseExps(lexer);
      exps.add(expRow);
      int row = 1;
      int col = expRow.length;
      boolean isInt = true;
      boolean isFloat = true;
      boolean isVar = true;
      if (!checkInt(expRow)) {
         isInt = false;
      }

      if (!checkFloat(expRow)) {
         isFloat = false;
      }

      if (!checkVar(expRow)) {
         isVar = false;
      }

      while (lexer.peekToken().enumToken == EnumToken.DCOMMA) {
         lexer.nextToken();
         expRow = parseExps(lexer);
         if (expRow.length != col) {
            throw new RuntimeException("bad matrix");
         }

         exps.add(expRow);
         row++;
         if (isInt && !checkInt(expRow)) {
            isInt = false;
         }

         if (isFloat && !checkFloat(expRow)) {
            isFloat = false;
         }

         if (isVar && !checkVar(expRow)) {
            isVar = false;
         }
      }

      int lastLine = lexer.nextToken(EnumToken.RPAREN).line;
      if (row == 1 && col == 1) {
         return exps.get(0)[0];
      } else if (isInt) {
         int[][] val = new int[row][col];

         for (int i = 0; i < row; i++) {
            expRow = exps.get(i);

            for (int j = 0; j < col; j++) {
               val[i][j] = ((Expression.IntegerExp)expRow[j]).val;
            }
         }

         return new Expression.IntegerMatrixExp(line, lastLine, val);
      } else if (isFloat) {
         double[][] val = new double[row][col];

         for (int i = 0; i < row; i++) {
            expRow = exps.get(i);

            for (int j = 0; j < col; j++) {
               if (expRow[j] instanceof Expression.FloatExp) {
                  val[i][j] = ((Expression.FloatExp)expRow[j]).val;
               } else {
                  val[i][j] = ((Expression.IntegerExp)expRow[j]).val;
               }
            }
         }

         return new Expression.FloatMatrixExp(line, lastLine, val);
      } else if (!isVar) {
         return new Expression.MatrixExp(line, lastLine, exps.toArray(new Expression[exps.size()][]));
      } else {
         Expression.NameExp[][] names = new Expression.NameExp[row][col];

         for (int i = 0; i < row; i++) {
            expRow = exps.get(i);

            for (int jx = 0; jx < col; jx++) {
               names[i][jx] = (Expression.NameExp)expRow[jx];
            }
         }

         return new Expression.NameMatrixExp(line, lastLine, names);
      }
   }

   private static boolean checkInt(Expression[] exps) {
      for (Expression exp : exps) {
         if (!(exp instanceof Expression.IntegerExp)) {
            return false;
         }
      }

      return true;
   }

   private static boolean checkFloat(Expression[] exps) {
      for (Expression exp : exps) {
         if (!(exp instanceof Expression.FloatExp) && !(exp instanceof Expression.IntegerExp)) {
            return false;
         }
      }

      return true;
   }

   private static boolean checkVar(Expression[] exps) {
      for (Expression exp : exps) {
         if (!(exp instanceof Expression.NameExp)) {
            return false;
         }
      }

      return true;
   }

   private static Expression parseExp7(Lexer lexer) {
      Expression exp = parseExp6(lexer);

      while (lexer.peekToken().enumToken == EnumToken.OR) {
         Token token = lexer.nextToken();
         exp = Optimize.optimizeLogicalOr(new Expression.BinopExp(token.line, token.enumToken, exp, parseExp6(lexer)));
      }

      return exp;
   }

   private static Expression parseExp6(Lexer lexer) {
      Expression exp = parseExp5(lexer);

      while (lexer.peekToken().enumToken == EnumToken.AND) {
         Token token = lexer.nextToken();
         exp = Optimize.optimizeLogicalAnd(new Expression.BinopExp(token.line, token.enumToken, exp, parseExp5(lexer)));
      }

      return exp;
   }

   private static Expression parseExp5(Lexer lexer) {
      Expression exp = parseExp4(lexer);

      while (isExp5(lexer.peekToken())) {
         Token token = lexer.nextToken();
         exp = Optimize.optimizeArithBinaryOp(new Expression.BinopExp(token.line, token.enumToken, exp, parseExp4(lexer)));
      }

      return exp;
   }

   private static Expression parseExp4(Lexer lexer) {
      Expression exp = parseExp3(lexer);

      while (isExp4(lexer.peekToken())) {
         Token token = lexer.nextToken();
         exp = Optimize.optimizeArithBinaryOp(new Expression.BinopExp(token.line, token.enumToken, exp, parseExp3(lexer)));
      }

      return exp;
   }

   private static Expression parseExp3(Lexer lexer) {
      Expression exp = parseExp2(lexer);

      while (isExp3(lexer.peekToken())) {
         Token token = lexer.nextToken();
         exp = Optimize.optimizeArithBinaryOp(new Expression.BinopExp(token.line, token.enumToken, exp, parseExp2(lexer)));
      }

      return exp;
   }

   private static Expression parseExp2(Lexer lexer) {
      if (isExp2(lexer.peekToken())) {
         Token token = lexer.nextToken();
         return Optimize.optimizeUnaryOp(new Expression.UnopExp(token.line, token.enumToken, parseExp2(lexer)));
      } else {
         return parseExp1(lexer);
      }
   }

   private static Expression parseExp1(Lexer lexer) {
      Expression exp = parseExp0(lexer);
      if (lexer.peekToken().enumToken == EnumToken.POW) {
         Token token = lexer.nextToken();
         exp = Optimize.optimizeArithBinaryOp(new Expression.BinopExp(token.line, token.enumToken, exp, parseExp2(lexer)));
      }

      return exp;
   }

   private static Expression parseExp0(Lexer lexer) {
      switch (lexer.peekToken().enumToken) {
         case LPAREN:
         case IDENTIFIER:
            return parseAssignOrFunctionCallExp(lexer);
         case NUMBER:
            return parseNumberExp(lexer);
         default:
            throw new RuntimeException("need matrix, function call, assign expression, var, number");
      }
   }

   private static Expression parseNumberExp(Lexer lexer) {
      Token token = lexer.nextToken(EnumToken.NUMBER);
      return (Expression)(token.token.contains(".")
         ? new Expression.FloatExp(token.line, Float.parseFloat(token.token))
         : new Expression.IntegerExp(token.line, Integer.parseInt(token.token)));
   }

   private static boolean isEnd(Token token) {
      return token.enumToken == EnumToken.EOF || token.enumToken == EnumToken.RPAREN;
   }

   private static boolean isExp5(Token token) {
      switch (token.enumToken) {
         case LT:
         case LE:
         case GT:
         case GE:
         case EQ:
         case NEQ:
            return true;
         default:
            return false;
      }
   }

   private static boolean isExp4(Token token) {
      switch (token.enumToken) {
         case ADD:
         case MINUS:
         case SUB:
            return true;
         default:
            return false;
      }
   }

   private static boolean isExp3(Token token) {
      switch (token.enumToken) {
         case MUL:
         case DIV:
         case MOD:
            return true;
         default:
            return false;
      }
   }

   private static boolean isExp2(Token token) {
      switch (token.enumToken) {
         case MINUS:
         case NEG:
         case NOT:
            return true;
         default:
            return false;
      }
   }
}
