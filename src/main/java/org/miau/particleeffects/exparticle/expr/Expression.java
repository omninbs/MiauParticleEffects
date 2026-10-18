package org.miau.particleeffects.exparticle.expr;

public class Expression {
   public final int line;
   public int returnType;

   protected Expression(int line, int returnType) {
      this.line = line;
      this.returnType = returnType;
   }

   public static class AssignExp extends Expression {
      public final Expression[] varList;
      public final Expression[] expList;

      public AssignExp(int line, Expression[] varList, Expression[] expList) {
         super(line, expList[expList.length - 1].returnType);
         this.varList = varList;
         this.expList = expList;
      }

      @Override
      public String toString() {
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < this.varList.length; i++) {
            sb.append(this.varList[i].toString()).append(",");
         }

         sb.deleteCharAt(sb.length() - 1).append("=");

         for (int i = 0; i < this.expList.length; i++) {
            sb.append(this.expList[i].toString()).append(",");
         }

         return sb.deleteCharAt(sb.length() - 1).toString();
      }
   }

   public static class BinopExp extends Expression {
      public final EnumToken op;
      public final Expression lexp;
      public final Expression rexp;

      public BinopExp(int line, EnumToken op, Expression lexp, Expression rexp) {
         super(line, -1);
         if (op == EnumToken.MINUS || op == EnumToken.NEG) {
            op = EnumToken.SUB;
         }

         this.op = op;
         this.lexp = lexp;
         this.rexp = rexp;
         if (op == EnumToken.AND || op == EnumToken.OR) {
            this.returnType = 10;
         }

         if (lexp.returnType != -1 && rexp.returnType != -1) {
            if (lexp.returnType == 10 && rexp.returnType == 10) {
               this.returnType = op == EnumToken.POW ? 7 : 10;
            } else if ((lexp.returnType != 10 || rexp.returnType != 7)
               && (lexp.returnType != 7 || rexp.returnType != 10)
               && (lexp.returnType != 7 || rexp.returnType != 7)) {
               if (lexp.returnType != 12 || rexp.returnType != 10 && rexp.returnType != 12) {
                  this.returnType = 13;
               } else {
                  this.returnType = 12;
               }
            } else {
               this.returnType = 7;
            }
         }
      }

      @Override
      public String toString() {
         return this.lexp.toString() + this.op.token + this.rexp.toString();
      }
   }

   public static class FloatExp extends Expression {
      public final double val;

      public FloatExp(int line, double val) {
         super(line, 7);
         this.val = val;
      }

      @Override
      public String toString() {
         return String.valueOf(this.val);
      }
   }

   public static class FloatMatrixExp extends Expression {
      public final int lastLine;
      public final double[][] val;

      public FloatMatrixExp(int line, int lastLine, double[][] val) {
         super(line, 13);
         this.lastLine = lastLine;
         this.val = val;
      }

      @Override
      public String toString() {
         StringBuilder sb = new StringBuilder();
         sb.append("(");

         for (int i = 0; i < this.val.length; i++) {
            for (int j = 0; j < this.val[i].length; j++) {
               sb.append(this.val[i][j]).append(",");
            }

            sb.deleteCharAt(sb.length() - 1).append(",,");
         }

         sb.delete(sb.length() - 2, sb.length()).append(")");
         return sb.toString();
      }
   }

   public static class FunctionCallExp extends Expression {
      public final int lastLine;
      public final String name;
      public final Expression[] args;

      public FunctionCallExp(int line, int lastLine, String name, Expression[] args) {
         super(line, -1);
         this.lastLine = lastLine;
         this.name = name;
         this.args = args;
      }

      @Override
      public String toString() {
         StringBuilder sb = new StringBuilder();
         sb.append(this.name.toString()).append("(");

         for (int i = 0; i < this.args.length; i++) {
            sb.append(this.args[i].toString()).append(",");
         }

         return sb.deleteCharAt(sb.length() - 1).append(")").toString();
      }
   }

   public static class IntegerExp extends Expression {
      public final int val;

      public IntegerExp(int line, int val) {
         super(line, 10);
         this.val = val;
      }

      @Override
      public String toString() {
         return String.valueOf(this.val);
      }
   }

   public static class IntegerMatrixExp extends Expression {
      public final int lastLine;
      public final int[][] val;

      public IntegerMatrixExp(int line, int lastLine, int[][] val) {
         super(line, 12);
         this.lastLine = lastLine;
         this.val = val;
      }

      @Override
      public String toString() {
         StringBuilder sb = new StringBuilder();
         sb.append("(");

         for (int i = 0; i < this.val.length; i++) {
            for (int j = 0; j < this.val[i].length; j++) {
               sb.append(this.val[i][j]).append(",");
            }

            sb.deleteCharAt(sb.length() - 1).append(",,");
         }

         sb.delete(sb.length() - 2, sb.length()).append(")");
         return sb.toString();
      }
   }

   public static class MatrixExp extends Expression {
      public final int lastLine;
      public final Expression[][] exps;

      public MatrixExp(int line, int lastLine, Expression[][] exps) {
         super(line, 13);
         this.lastLine = lastLine;
         this.exps = exps;
      }

      @Override
      public String toString() {
         StringBuilder sb = new StringBuilder();
         sb.append("(");

         for (int i = 0; i < this.exps.length; i++) {
            for (int j = 0; j < this.exps[i].length; j++) {
               sb.append(this.exps[i][j].toString()).append(",");
            }

            sb.deleteCharAt(sb.length() - 1).append(",,");
         }

         sb.delete(sb.length() - 2, sb.length()).append(")");
         return sb.toString();
      }
   }

   public static class NameExp extends Expression {
      public final String name;

      public NameExp(int line, String name) {
         super(line, -1);
         this.name = name;
      }

      @Override
      public String toString() {
         return this.name;
      }
   }

   public static class NameMatrixExp extends Expression {
      public final int lastLine;
      public final Expression.NameExp[][] names;

      public NameMatrixExp(int line, int lastLine, Expression.NameExp[][] names) {
         super(line, 0);
         this.lastLine = lastLine;
         this.names = names;
      }

      @Override
      public String toString() {
         StringBuilder sb = new StringBuilder();
         sb.append("(");

         for (int i = 0; i < this.names.length; i++) {
            for (int j = 0; j < this.names[i].length; j++) {
               sb.append(this.names[i][j].name).append(",");
            }

            sb.deleteCharAt(sb.length() - 1).append(",,");
         }

         sb.delete(sb.length() - 2, sb.length()).append(")");
         return sb.toString();
      }
   }

   public static class UnopExp extends Expression {
      public final EnumToken op;
      public final Expression exp;

      public UnopExp(int line, EnumToken op, Expression exp) {
         super(line, exp.returnType);
         if (op == EnumToken.MINUS || op == EnumToken.SUB) {
            op = EnumToken.NEG;
         }

         this.op = op;
         this.exp = exp;
      }

      @Override
      public String toString() {
         return this.op.token + this.exp.toString();
      }
   }
}
