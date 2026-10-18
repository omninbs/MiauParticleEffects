package org.miau.particleeffects.exparticle.expr;

public class ExpressionUtil {
   public ExpressionUtil() {
   }

   public static IExecutable prase(String expression) {
      if (expression != null && !expression.equals("null")) {
         try {
            return ClassExpression.parse(expression);
         } catch (RuntimeException var2) {
            ExprLog.error(var2);
            throw var2;
         }
      } else {
         return null;
      }
   }
}
