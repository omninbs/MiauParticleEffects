package org.miau.particleeffects.exparticle.expr;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

public class CodeGen {
   public static final int T_UNKNOW = -1;
   public static final int T_VOID = 0;
   public static final int T_INTMAT = 12;
   public static final int T_DOUBLEMAT = 13;
   private static final Map<EnumToken, Integer> IOPTOOP = new HashMap<>();
   private static final Map<EnumToken, Integer> DOPTOOP = new HashMap<>();
   private static final Map<EnumToken, Integer> DIOPTOOP = new HashMap<>();
   private static final List<String> FIELDS = Arrays.asList(
      "PI",
      "E",
      "x",
      "y",
      "z",
      "s1",
      "s2",
      "dis",
      "t",
      "cr",
      "cg",
      "cb",
      "alpha",
      "vx",
      "vy",
      "vz",
      "cx",
      "cy",
      "cz",
      "dx",
      "dy",
      "dz",
      "ds1",
      "ds2",
      "ddis",
      "age",
      "destory"
   );
   private static final Method[] METHODS = Math.class.getMethods();
   private Expression[] block;
   private ClassWriter cw = new ClassWriter(2);
   private MethodVisitor mv;
   private MethodVisitor simulationMv;
   private int simulationCount;
   private int maxLocal;
   private Map<String, CodeGen.LocalVarInfo> localVars = new HashMap<>();

   public CodeGen(Expression[] block) {
      this.block = Arrays.copyOf(block, block.length);
   }

   public Class<?> codeGenBlock(String name) {
      this.cw.visit(52, 33, "org/miau/particleeffects/exparticle/expr/CodeGen$" + name, null, "java/lang/Object", null);
      this.cw.visitInnerClass("org/miau/particleeffects/exparticle/expr/CodeGen$" + name, "org/miau/particleeffects/exparticle/expr/CodeGen", name, 9);
      this.mv = this.cw.visitMethod(9, "invoke", "(Lorg/miau/particleeffects/exparticle/expr/ParticleStruct;)I", null, null);
      this.addLocalVar("this", -1);
      this.mv.visitCode();

      for (int i = 0; i < this.block.length - 1; i++) {
         this.codeGenExp(this.block[i], 0);
      }

      this.codeGenReturn(this.block[this.block.length - 1]);
      this.mv.visitMaxs(0, 0);
      this.mv.visitEnd();
      this.cw.visitEnd();
      byte[] bytes = this.cw.toByteArray();
      return new ExpressionClassLoader(Thread.currentThread().getContextClassLoader())
            .define("org.miau.particleeffects.exparticle.expr.CodeGen$" + name, bytes);
   }

   /**
    * Java 9+ 起 ClassLoader.defineClass 被模块封装保护，反射调用会抛异常；
    * 这里用一个子类公开 define 方法，仅用于加载生成的表达式字节码。
    */
   private static final class ExpressionClassLoader extends ClassLoader {
      ExpressionClassLoader(ClassLoader parent) {
         super(parent);
      }

      Class<?> define(String name, byte[] bytes) {
         return defineClass(name, bytes, 0, bytes.length);
      }
   }

   private int codeGenExp(Expression exp, int targetType) {
      if (exp instanceof Expression.IntegerExp) {
         return this.codeGenTypeTransform(this.codeGenLoadInteger(((Expression.IntegerExp)exp).val), targetType);
      } else if (exp instanceof Expression.FloatExp) {
         return this.codeGenTypeTransform(this.codeGenLoadFloat(((Expression.FloatExp)exp).val), targetType);
      } else if (exp instanceof Expression.IntegerMatrixExp) {
         return this.codeGenTypeTransform(this.codeGenLoadIntegerMatrix(((Expression.IntegerMatrixExp)exp).val), targetType);
      } else if (exp instanceof Expression.FloatMatrixExp) {
         return this.codeGenTypeTransform(this.codeGenLoadFloatMatrix(((Expression.FloatMatrixExp)exp).val), targetType);
      } else if (exp instanceof Expression.MatrixExp) {
         return this.codeGenTypeTransform(this.codeGenLoadMatrix(((Expression.MatrixExp)exp).exps), targetType);
      } else if (exp instanceof Expression.UnopExp) {
         return this.codeGenTypeTransform(this.codeGenUnopExp(((Expression.UnopExp)exp).op, ((Expression.UnopExp)exp).exp), targetType);
      } else if (exp instanceof Expression.BinopExp) {
         return this.codeGenTypeTransform(this.codeGenBinopExp((Expression.BinopExp)exp), targetType);
      } else if (exp instanceof Expression.NameExp) {
         return this.codeGenTypeTransform(this.codeGenNameExp(((Expression.NameExp)exp).name, targetType), targetType);
      } else if (exp instanceof Expression.FunctionCallExp) {
         return this.codeGenTypeTransform(
            this.codeGenFunctionCallExp(((Expression.FunctionCallExp)exp).name, ((Expression.FunctionCallExp)exp).args), targetType
         );
      } else {
         return exp instanceof Expression.AssignExp
            ? this.codeGenTypeTransform(
               this.codeGenAssignExp(((Expression.AssignExp)exp).varList, ((Expression.AssignExp)exp).expList, targetType != 0), targetType
            )
            : 0;
      }
   }

   private void codeGenReturn(Expression exp) {
      this.codeGenExp(exp, 10);
      this.mv.visitInsn(172);
   }

   private int codeGenLoadInteger(int n) {
      if (n >= -1 && n < 6) {
         this.mv.visitInsn(3 + n);
      } else if (n >= -128 && n < 128) {
         this.mv.visitIntInsn(16, n);
      } else if (n >= -32768 && n < 32768) {
         this.mv.visitIntInsn(17, n);
      } else {
         this.mv.visitLdcInsn(n);
      }

      return 10;
   }

   private int codeGenLoadFloat(double n) {
      if (n == 0.0) {
         this.mv.visitInsn(14);
      } else if (n == 1.0) {
         this.mv.visitInsn(15);
      } else {
         this.mv.visitLdcInsn(n);
      }

      return 7;
   }

   private int codeGenLoadIntegerMatrix(int[][] val) {
      this.codeGenLoadInteger(val.length);
      this.mv.visitTypeInsn(189, "[I");

      for (int i = 0; i < val.length; i++) {
         this.mv.visitInsn(89);
         this.codeGenLoadInteger(i);
         this.codeGenLoadInteger(val[i].length);
         this.mv.visitIntInsn(188, 10);

         for (int j = 0; j < val[i].length; j++) {
            this.mv.visitInsn(89);
            this.codeGenLoadInteger(j);
            this.codeGenLoadInteger(val[i][j]);
            this.mv.visitInsn(79);
         }

         this.mv.visitInsn(83);
      }

      return 12;
   }

   private int codeGenLoadFloatMatrix(double[][] val) {
      this.codeGenLoadInteger(val.length);
      this.mv.visitTypeInsn(189, "[D");

      for (int i = 0; i < val.length; i++) {
         this.mv.visitInsn(89);
         this.codeGenLoadInteger(i);
         this.codeGenLoadInteger(val[i].length);
         this.mv.visitIntInsn(188, 7);

         for (int j = 0; j < val[i].length; j++) {
            this.mv.visitInsn(89);
            this.codeGenLoadInteger(j);
            this.codeGenLoadFloat(val[i][j]);
            this.mv.visitInsn(82);
         }

         this.mv.visitInsn(83);
      }

      return 13;
   }

   private int codeGenLoadMatrix(Expression[][] exps) {
      this.codeGenLoadInteger(exps.length);
      this.mv.visitTypeInsn(189, "[D");

      for (int i = 0; i < exps.length; i++) {
         this.mv.visitInsn(89);
         this.codeGenLoadInteger(i);
         this.codeGenLoadInteger(exps[i].length);
         this.mv.visitIntInsn(188, 7);

         for (int j = 0; j < exps[i].length; j++) {
            this.mv.visitInsn(89);
            this.codeGenLoadInteger(j);
            this.codeGenExp(exps[i][j], 7);
            this.mv.visitInsn(82);
         }

         this.mv.visitInsn(83);
      }

      return 13;
   }

   private int codeGenUnopExp(EnumToken op, Expression exp) {
      switch (op) {
         case NEG:
            switch (this.codeGenExp(exp, -1)) {
               case 7:
                  this.mv.visitInsn(119);
                  return 7;
               case 8:
               case 9:
               case 11:
               default:
                  throw new RuntimeException("bad type");
               case 10:
                  this.mv.visitInsn(116);
                  return 10;
               case 12:
                  this.mv.visitMethodInsn(184, "org/miau/particleeffects/exparticle/expr/MatrixUtil", "matNeg", "([[I)[[I", false);
                  return 12;
               case 13:
                  this.mv.visitMethodInsn(184, "org/miau/particleeffects/exparticle/expr/MatrixUtil", "matNeg", "([[D)[[D", false);
                  return 13;
            }
         case NOT:
            switch (this.codeGenExp(exp, -1)) {
               case 7:
                  this.mv.visitInsn(14);
                  this.mv.visitInsn(151);
                  Label dJumpLabel = new Label();
                  this.mv.visitJumpInsn(153, dJumpLabel);
                  this.mv.visitInsn(14);
                  Label dEndLabel = new Label();
                  this.mv.visitJumpInsn(167, dEndLabel);
                  this.mv.visitLabel(dJumpLabel);
                  this.mv.visitInsn(15);
                  this.mv.visitLabel(dEndLabel);
                  return 7;
               case 10:
                  Label iJumpLabel = new Label();
                  this.mv.visitJumpInsn(153, iJumpLabel);
                  this.mv.visitInsn(3);
                  Label iEndLabel = new Label();
                  this.mv.visitJumpInsn(167, iEndLabel);
                  this.mv.visitLabel(iJumpLabel);
                  this.mv.visitInsn(4);
                  this.mv.visitLabel(iEndLabel);
                  return 10;
               default:
                  throw new RuntimeException("bad type");
            }
         default:
            throw new RuntimeException("bad operator: " + op);
      }
   }

   private int codeGenBinopExp(Expression.BinopExp exp) {
      switch (exp.op) {
         case AND:
            this.codeGenExp(exp.lexp, 10);
            Label aJumpLabel = new Label();
            this.mv.visitJumpInsn(153, aJumpLabel);
            this.codeGenExp(exp.rexp, 10);
            this.mv.visitJumpInsn(153, aJumpLabel);
            this.mv.visitInsn(4);
            Label aEndLabel = new Label();
            this.mv.visitJumpInsn(167, aEndLabel);
            this.mv.visitLabel(aJumpLabel);
            this.mv.visitInsn(3);
            this.mv.visitLabel(aEndLabel);
            return 10;
         case OR:
            this.codeGenExp(exp.lexp, 10);
            Label oJumpLabel = new Label();
            this.mv.visitJumpInsn(154, oJumpLabel);
            this.codeGenExp(exp.rexp, 10);
            this.mv.visitJumpInsn(154, oJumpLabel);
            this.mv.visitInsn(3);
            Label oEndLabel = new Label();
            this.mv.visitJumpInsn(167, oEndLabel);
            this.mv.visitLabel(oJumpLabel);
            this.mv.visitInsn(4);
            this.mv.visitLabel(oEndLabel);
            return 10;
         default:
            if (exp.returnType == -1) {
               this.startSimulation();
               exp.returnType = this.upwardType(this.codeGenExp(exp.lexp, -1), this.codeGenExp(exp.rexp, -1));
               this.stopSimulation();
            }

            switch (exp.returnType) {
               case 7:
                  this.codeGenExp(exp.lexp, 7);
                  this.codeGenExp(exp.rexp, 7);
                  switch (exp.op) {
                     case ADD:
                        this.mv.visitInsn(99);
                        return 7;
                     case SUB:
                        this.mv.visitInsn(103);
                        return 7;
                     case MUL:
                        this.mv.visitInsn(107);
                        return 7;
                     case DIV:
                        this.mv.visitInsn(111);
                        return 7;
                     case MOD:
                        this.mv.visitInsn(115);
                        return 7;
                     case POW:
                        this.mv.visitMethodInsn(184, "java/lang/Math", "pow", "(DD)D", false);
                        return 7;
                     case LT:
                     case LE:
                     case GT:
                     case GE:
                     case EQ:
                     case NEQ:
                        this.mv.visitInsn(DOPTOOP.get(exp.op));
                        Label logicJumpLabel = new Label();
                        this.mv.visitJumpInsn(DIOPTOOP.get(exp.op), logicJumpLabel);
                        this.mv.visitInsn(4);
                        Label logicEndLabel = new Label();
                        this.mv.visitJumpInsn(167, logicEndLabel);
                        this.mv.visitLabel(logicJumpLabel);
                        this.mv.visitInsn(3);
                        this.mv.visitLabel(logicEndLabel);
                        return 10;
                     default:
                        throw new RuntimeException("bad operator: " + exp.op.token);
                  }
               case 10:
                  this.codeGenExp(exp.lexp, 10);
                  this.codeGenExp(exp.rexp, 10);
                  switch (exp.op) {
                     case ADD:
                        this.mv.visitInsn(96);
                        return 10;
                     case SUB:
                        this.mv.visitInsn(100);
                        return 10;
                     case MUL:
                        this.mv.visitInsn(104);
                        return 10;
                     case DIV:
                        this.mv.visitInsn(108);
                        return 10;
                     case MOD:
                        this.mv.visitInsn(112);
                        return 10;
                     case POW:
                        this.mv.visitMethodInsn(184, "org/miau/particleeffects/exparticle/expr/MatrixUtil", "pow", "(II)D", false);
                        return 7;
                     case LT:
                     case LE:
                     case GT:
                     case GE:
                     case EQ:
                     case NEQ:
                        Label logicJumpLabel = new Label();
                        this.mv.visitJumpInsn(IOPTOOP.get(exp.op), logicJumpLabel);
                        this.mv.visitInsn(4);
                        Label logicEndLabel = new Label();
                        this.mv.visitJumpInsn(167, logicEndLabel);
                        this.mv.visitLabel(logicJumpLabel);
                        this.mv.visitInsn(3);
                        this.mv.visitLabel(logicEndLabel);
                        return 10;
                     default:
                        throw new RuntimeException("bad operator: " + exp.op.token);
                  }
               default:
                  int ltype = this.codeGenExp(exp.lexp, -1);
                  int rtype = this.codeGenExp(exp.rexp, -1);
                  int returntype = 0;
                  String returnName = null;
                  String arg1Name = null;
                  switch (ltype) {
                     case 12:
                        arg1Name = "[[I";
                        break;
                     case 13:
                        arg1Name = "[[D";
                        returnName = "[[D";
                        returntype = 13;
                        break;
                     default:
                        throw new RuntimeException("the number must appear on the right side of the matrix");
                  }

                  String arg2Name = null;
                  switch (rtype) {
                     case 7:
                        arg2Name = "D";
                        returnName = "[[D";
                        returntype = 13;
                        break;
                     case 8:
                     case 9:
                     case 11:
                     default:
                        throw new RuntimeException("bad type: " + rtype);
                     case 10:
                        arg2Name = "I";
                        break;
                     case 12:
                        arg2Name = "[[I";
                        break;
                     case 13:
                        arg2Name = "[[D";
                        returnName = "[[D";
                        returntype = 13;
                  }

                  if (returnName == null) {
                     returnName = "[[I";
                     returntype = 12;
                  }

                  String functionName = null;
                  switch (exp.op) {
                     case ADD:
                        functionName = "matAdd";
                        break;
                     case SUB:
                        functionName = "matSub";
                        break;
                     case MUL:
                        functionName = "matMul";
                        break;
                     case DIV:
                        functionName = "matDiv";
                        break;
                     case MOD:
                        functionName = "matMod";
                        break;
                     case POW:
                        functionName = "matPow";
                        break;
                     default:
                        throw new RuntimeException("bad operator: " + exp.op.token);
                  }

                  if ((!functionName.equals("matDiv") && !functionName.equals("matMod") || !arg2Name.startsWith("[["))
                     && (!functionName.equals("matPow") || arg2Name.equals("I"))) {
                     this.mv.visitMethodInsn(184, "org/miau/particleeffects/exparticle/expr/MatrixUtil", functionName, "(" + arg1Name + arg2Name + ")" + returnName, false);
                     return returntype;
                  } else {
                     throw new RuntimeException("bad operator: " + exp.op.token);
                  }
            }
      }
   }

   private int codeGenNameExp(String name, int targetType) {
      if (FIELDS.contains(name)) {
         this.mv.visitVarInsn(25, 0);
         this.mv.visitFieldInsn(180, "org/miau/particleeffects/exparticle/expr/ParticleStruct", name, "D");
         return 7;
      } else if (this.localVars.containsKey(name)) {
         CodeGen.LocalVarInfo info = this.localVars.get(name);
         switch (info.type) {
            case 7:
               this.mv.visitVarInsn(24, info.index);
               return 7;
            case 8:
            case 9:
            case 11:
            default:
               throw new RuntimeException("bad type: " + info.type);
            case 10:
               this.mv.visitVarInsn(21, info.index);
               return 10;
            case 12:
               this.mv.visitVarInsn(25, info.index);
               return 12;
            case 13:
               this.mv.visitVarInsn(25, info.index);
               return 13;
         }
      } else {
         throw new RuntimeException("undefine var: " + name);
      }
   }

   private int codeGenFunctionCallExp(String name, Expression[] args) {
      List<Method> methods = new ArrayList<>();

      for (Method method : METHODS) {
         if (method.getName().equals(name) && method.getParameterCount() == args.length) {
            methods.add(method);
         }
      }

      if (methods.isEmpty()) {
         throw new RuntimeException("function not found: " + name);
      } else {
         for (int i = 0; i < args.length; i++) {
            if (args[i].returnType == -1) {
               this.startSimulation();
               args[i].returnType = this.codeGenExp(args[i], -1);
               this.stopSimulation();
            }
         }

         int maxSimilarity = 0;
         int maxSimilarityIndex = -1;
         if (args.length <= 0) {
            maxSimilarityIndex = 0;
         } else {
            int ix = 0;

            while (ix < methods.size()) {
               Method methodx = methods.get(ix);
               Class<?>[] parameterTypes = methodx.getParameterTypes();
               int similarity = 0;
               int j = 0;

               while (true) {
                  label114: {
                     if (j < args.length) {
                        switch (args[j].returnType) {
                           case 7:
                              if (parameterTypes[j] == int.class) {
                                 similarity += 4;
                                 break label114;
                              }

                              if (parameterTypes[j] == double.class) {
                                 similarity += 6;
                                 break label114;
                              }
                              break;
                           case 8:
                           case 9:
                           case 11:
                           default:
                              throw new RuntimeException("bad type: " + args[j].returnType);
                           case 10:
                              if (parameterTypes[j] == int.class) {
                                 similarity += 6;
                                 break label114;
                              }

                              if (parameterTypes[j] == double.class) {
                                 similarity += 5;
                                 break label114;
                              }
                              break;
                           case 12:
                              if (parameterTypes[j] == int.class) {
                                 similarity += 3;
                                 break label114;
                              }

                              if (parameterTypes[j] == double.class) {
                                 similarity += 2;
                                 break label114;
                              }
                              break;
                           case 13:
                              if (parameterTypes[j] == int.class) {
                                 similarity++;
                                 break label114;
                              }

                              if (parameterTypes[j] == double.class) {
                                 similarity += 3;
                                 break label114;
                              }
                        }
                     } else if (similarity > maxSimilarity) {
                        maxSimilarity = similarity;
                        maxSimilarityIndex = ix;
                     }

                     ix++;
                     break;
                  }

                  j++;
               }
            }
         }

         if (maxSimilarityIndex == -1) {
            throw new RuntimeException("function not found: " + name);
         } else {
            Method methodx = methods.get(maxSimilarityIndex);
            Class<?>[] parameterTypes = methodx.getParameterTypes();
            Class<?> returnType = methodx.getReturnType();
            StringBuilder sb = new StringBuilder();
            sb.append("(");

            for (int ix = 0; ix < args.length; ix++) {
               this.codeGenExp(args[ix], parameterTypes[ix] == int.class ? 10 : 7);
               sb.append(parameterTypes[ix] == int.class ? "I" : "D");
            }

            sb.append(")");
            if (returnType == long.class) {
               sb.append("J");
               this.mv.visitMethodInsn(184, "java/lang/Math", name, sb.toString(), false);
               this.mv.visitInsn(136);
               return 10;
            } else {
               sb.append(returnType == int.class ? "I" : "D");
               this.mv.visitMethodInsn(184, "java/lang/Math", name, sb.toString(), false);
               return returnType == int.class ? 10 : 7;
            }
         }
      }
   }

   private int codeGenAssignExp(Expression[] varList, Expression[] expList, boolean needReturn) {
      int[] types = new int[expList.length];

      for (int i = 0; i < expList.length; i++) {
         if (varList[i] instanceof Expression.NameExp && FIELDS.contains(((Expression.NameExp)varList[i]).name)) {
            this.mv.visitVarInsn(25, 0);
         }

         types[i] = this.codeGenExp(expList[i], -1);
      }

      if (needReturn) {
         this.mv.visitInsn(types[expList.length - 1] == 7 ? 92 : 89);
         this.codeGenStore("__RETURN", types[expList.length - 1]);
      }

      for (int i = varList.length - 1; i >= 0; i--) {
         if (varList[i] instanceof Expression.NameExp) {
            this.codeGenStore(((Expression.NameExp)varList[i]).name, types[i]);
         } else if (varList[i] instanceof Expression.NameMatrixExp) {
            if (types[i] != 12 && types[i] != 13) {
               throw new RuntimeException("can't deconstruction number: " + types[i]);
            }

            Expression.NameExp[][] names = ((Expression.NameMatrixExp)varList[i]).names;

            for (int j = 0; j < names.length; j++) {
               if (j < names.length - 1) {
                  this.mv.visitInsn(89);
               }

               this.codeGenLoadInteger(j);
               this.mv.visitInsn(50);

               for (int k = 0; k < names[j].length; k++) {
                  if (k < names[j].length - 1) {
                     this.mv.visitInsn(89);
                  }

                  this.codeGenLoadInteger(k);
                  this.mv.visitInsn(types[i] == 12 ? 46 : 49);
                  this.codeGenStore("__TEMP", types[i] == 12 ? 10 : 7);
                  this.mv.visitVarInsn(25, 0);
                  this.codeGenNameExp("__TEMP", types[i] == 12 ? 10 : 7);
                  this.codeGenStore(names[j][k].name, types[i] == 12 ? 10 : 7);
               }
            }
         }
      }

      if (needReturn) {
         this.codeGenNameExp("__RETURN", types[expList.length - 1]);
      }

      return needReturn ? types[expList.length - 1] : 0;
   }

   private int codeGenTypeTransform(int sourceType, int targetType) {
      if (targetType == -1) {
         return sourceType;
      } else if (sourceType == targetType) {
         return targetType;
      } else if (targetType == 0) {
         switch (sourceType) {
            case 7:
               this.mv.visitInsn(88);
               break;
            default:
               this.mv.visitInsn(87);
         }

         return targetType;
      } else {
         switch (sourceType) {
            case 7:
               switch (targetType) {
                  case 10:
                     this.mv.visitInsn(142);
                     return targetType;
                  case 11:
                  default:
                     throw new RuntimeException("bad type: " + targetType);
                  case 12:
                     this.mv.visitInsn(142);
                     this.mv.visitMethodInsn(184, "org/miau/particleeffects/exparticle/expr/MatrixUtil", "toMat", "(I)[[I", false);
                     return targetType;
                  case 13:
                     this.mv.visitMethodInsn(184, "org/miau/particleeffects/exparticle/expr/MatrixUtil", "toMat", "(D)[[D", false);
                     return targetType;
               }
            case 8:
            case 9:
            case 11:
            default:
               throw new RuntimeException("bad type: " + targetType);
            case 10:
               switch (targetType) {
                  case 7:
                     this.mv.visitInsn(135);
                     return targetType;
                  case 12:
                     this.mv.visitMethodInsn(184, "org/miau/particleeffects/exparticle/expr/MatrixUtil", "toMat", "(I)[[I", false);
                     return targetType;
                  case 13:
                     this.mv.visitInsn(135);
                     this.mv.visitMethodInsn(184, "org/miau/particleeffects/exparticle/expr/MatrixUtil", "toMat", "(D)[[D", false);
                     return targetType;
                  default:
                     throw new RuntimeException("bad type: " + targetType);
               }
            case 12:
               switch (targetType) {
                  case 7:
                     this.mv.visitMethodInsn(184, "org/miau/particleeffects/exparticle/expr/MatrixUtil", "toNumber", "([[I)I", false);
                     this.mv.visitInsn(135);
                     return targetType;
                  case 10:
                     this.mv.visitMethodInsn(184, "org/miau/particleeffects/exparticle/expr/MatrixUtil", "toNumber", "([[I)I", false);
                     return targetType;
                  case 13:
                     this.mv.visitMethodInsn(184, "org/miau/particleeffects/exparticle/expr/MatrixUtil", "matToMat", "([[I)D[[", false);
                     return targetType;
                  default:
                     throw new RuntimeException("bad type: " + targetType);
               }
            case 13:
               switch (targetType) {
                  case 7:
                     this.mv.visitMethodInsn(184, "org/miau/particleeffects/exparticle/expr/MatrixUtil", "toNumber", "([[D)D", false);
                     return targetType;
                  case 10:
                     this.mv.visitMethodInsn(184, "org/miau/particleeffects/exparticle/expr/MatrixUtil", "toNumber", "([[D)D", false);
                     this.mv.visitInsn(142);
                     return targetType;
                  case 12:
                     this.mv.visitMethodInsn(184, "org/miau/particleeffects/exparticle/expr/MatrixUtil", "matToMat", "([[D)I[[", false);
                     return targetType;
                  default:
                     throw new RuntimeException("bad type: " + targetType);
               }
         }
      }
   }

   private int codeGenStore(String name, int targetType) {
      if (FIELDS.contains(name)) {
         this.codeGenTypeTransform(targetType, 7);
         this.mv.visitFieldInsn(181, "org/miau/particleeffects/exparticle/expr/ParticleStruct", name, "D");
         return 7;
      } else {
         if (!this.localVars.containsKey(name)) {
            this.addLocalVar(name, targetType);
         }

         CodeGen.LocalVarInfo info = this.localVars.get(name);
         switch (info.type) {
            case 7:
               this.codeGenTypeTransform(targetType, 7);
               this.mv.visitVarInsn(57, info.index);
               return 7;
            case 8:
            case 9:
            case 11:
            default:
               throw new RuntimeException("bad type: " + info.type);
            case 10:
               this.codeGenTypeTransform(targetType, 10);
               this.mv.visitVarInsn(54, info.index);
               return 10;
            case 12:
               this.codeGenTypeTransform(targetType, 12);
               this.mv.visitVarInsn(58, info.index);
               return 12;
            case 13:
               this.codeGenTypeTransform(targetType, 13);
               this.mv.visitVarInsn(58, info.index);
               return 13;
         }
      }
   }

   private int upwardType(int ltype, int rtype) {
      if (Math.max(ltype, rtype) <= 0) {
         throw new RuntimeException("bad type");
      } else if (ltype == 10 && rtype == 10) {
         return 10;
      } else {
         return (ltype != 10 || rtype != 7) && (ltype != 7 || rtype != 10) && (ltype != 7 || rtype != 7) ? -1 : 7;
      }
   }

   private int addLocalVar(String name, int type) {
      int index = this.maxLocal;
      if (type == 7) {
         this.maxLocal += 2;
      } else {
         this.maxLocal++;
      }

      this.localVars.put(name, new CodeGen.LocalVarInfo(type, index));
      return index;
   }

   private void startSimulation() {
      if (this.simulationCount++ == 0) {
         this.simulationMv = this.mv;
         this.mv = new MethodVisitor(262144) {};
      }
   }

   private void stopSimulation() {
      if (--this.simulationCount == 0) {
         this.mv = this.simulationMv;
      }
   }

   static {
      IOPTOOP.put(EnumToken.LT, 162);
      IOPTOOP.put(EnumToken.LE, 163);
      IOPTOOP.put(EnumToken.GT, 164);
      IOPTOOP.put(EnumToken.GE, 161);
      IOPTOOP.put(EnumToken.EQ, 160);
      IOPTOOP.put(EnumToken.NEQ, 159);
      DOPTOOP.put(EnumToken.LT, 152);
      DOPTOOP.put(EnumToken.LE, 152);
      DOPTOOP.put(EnumToken.GT, 151);
      DOPTOOP.put(EnumToken.GE, 151);
      DOPTOOP.put(EnumToken.EQ, 151);
      DOPTOOP.put(EnumToken.NEQ, 151);
      DIOPTOOP.put(EnumToken.LT, 156);
      DIOPTOOP.put(EnumToken.LE, 157);
      DIOPTOOP.put(EnumToken.GT, 158);
      DIOPTOOP.put(EnumToken.GE, 155);
      DIOPTOOP.put(EnumToken.EQ, 154);
      DIOPTOOP.put(EnumToken.NEQ, 153);
   }

   private static class LocalVarInfo {
      private int type;
      private int index;

      public LocalVarInfo(int type, int index) {
         this.type = type;
         this.index = index;
      }
   }
}
