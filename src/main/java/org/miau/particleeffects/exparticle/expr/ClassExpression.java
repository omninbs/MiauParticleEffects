package org.miau.particleeffects.exparticle.expr;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ClassExpression implements IExecutable {
   private static Map<String, Method> cache = new HashMap<>();
   private static int index = 0;
   private ParticleStruct struct = new ParticleStruct();
   private Method method;

   private ClassExpression(String expression) {
      Lexer lexer = new Lexer(expression);
      Expression[] exps = Parser.parseBlock(lexer);
      CodeGen codeGen = new CodeGen(exps);
      Class<?> clazz = codeGen.codeGenBlock("EXP_" + index++);

      try {
         this.method = clazz.getMethod("invoke", ParticleStruct.class);
      } catch (SecurityException | NoSuchMethodException var7) {
         throw new RuntimeException(var7);
      }
   }

   private ClassExpression(Method method) {
      this.method = method;
   }

   public static ClassExpression parse(String expression) {
      if (cache.containsKey(expression)) {
         return new ClassExpression(cache.get(expression));
      } else {
         ClassExpression instance = new ClassExpression(expression);
         cache.put(expression, instance.method);
         return instance;
      }
   }

   @Override
   public ParticleStruct getData() {
      return this.struct;
   }

   @Override
   public int invoke() {
      try {
         return (Integer)this.method.invoke(null, this.struct);
      } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException var2) {
         ExprLog.error(var2);
         throw new RuntimeException(var2);
      }
   }
}
