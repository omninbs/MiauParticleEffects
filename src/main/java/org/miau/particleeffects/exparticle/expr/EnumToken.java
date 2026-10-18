package org.miau.particleeffects.exparticle.expr;

public enum EnumToken {
   EOF,
   SEMI(";"),
   COMMA(","),
   DCOMMA(",,"),
   DOT("."),
   LPAREN("("),
   RPAREN(")"),
   ASSIGN("="),
   MINUS("-"),
   NEG("-"),
   SUB("-"),
   ADD("+"),
   MUL("*"),
   DIV("/"),
   MOD("%"),
   POW("^"),
   NOT("!"),
   AND("&"),
   OR("|"),
   LT("<"),
   LE("<="),
   GT(">"),
   GE(">="),
   EQ("=="),
   NEQ("!="),
   NUMBER,
   IDENTIFIER;

   public final String token;

   private EnumToken() {
      this(null);
   }

   private EnumToken(String token) {
      this.token = token;
   }
}
