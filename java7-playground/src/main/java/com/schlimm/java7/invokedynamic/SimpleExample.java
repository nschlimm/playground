package com.schlimm.java7.invokedynamic;

//import java.util.*;
import java.io.FileOutputStream;

import com.sun.xml.internal.ws.org.objectweb.asm.AnnotationVisitor;
import com.sun.xml.internal.ws.org.objectweb.asm.ClassWriter;
import com.sun.xml.internal.ws.org.objectweb.asm.FieldVisitor;
import com.sun.xml.internal.ws.org.objectweb.asm.Label;
import com.sun.xml.internal.ws.org.objectweb.asm.MethodVisitor;
import com.sun.xml.internal.ws.org.objectweb.asm.Opcodes;

public class SimpleExample implements Opcodes 
{
  public static void main(String args[])
  {
      try 
      {
          byte[] classFile = dump();
          FileOutputStream fso = new FileOutputStream("Simple.class");
          fso.write(classFile);
          fso.close();
      }
      catch (Exception e) 
      {
          e.printStackTrace();
      }
  }

  public static byte[] dump () throws Exception 
  {

      ClassWriter cw = new ClassWriter(0);
      FieldVisitor fv;
      MethodVisitor mv;
      AnnotationVisitor av0;

      cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, "Simple", null, "java/lang/Object", null);

      {
          mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
          mv.visitCode();
          mv.visitVarInsn(ALOAD, 0);
          mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
          mv.visitInsn(RETURN);
          mv.visitMaxs(1, 1);
          mv.visitEnd();
      }

      {
          mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
          mv.visitCode();
          mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
          mv.visitLdcInsn("Will call an arbitrary JVM method using invokedynamic!");
          mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V");
          mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
          mv.visitLdcInsn("======================================================");
          mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V");
          mv.visitInsn(LCONST_0);
          mv.visitVarInsn(LSTORE, 1);
          Label l0 = new Label();
          mv.visitLabel(l0);
          mv.visitFrame(Opcodes.F_APPEND,1, new Object[] {Opcodes.LONG}, 0, null);
          mv.visitVarInsn(LLOAD, 1);  
          mv.visitLdcInsn(new Long(10L));
          mv.visitInsn(LCMP);
          Label l1 = new Label();
          mv.visitJumpInsn(IFGE, l1);
          mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
          mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
          mv.visitInsn(DUP);
          mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V");
          mv.visitLdcInsn("Ln: ");
          mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
          mv.visitVarInsn(LLOAD, 1);
          mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(J)Ljava/lang/StringBuilder;");
          mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
          mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V");
          mv.visitVarInsn(LLOAD, 1);
          mv.visitInsn(LCONST_1);
          mv.visitInsn(LADD);
          mv.visitVarInsn(LSTORE, 1);
          mv.visitJumpInsn(GOTO, l0);
          mv.visitLabel(l1);
          mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
          mv.visitInsn(RETURN);
          mv.visitMaxs(4, 3);
          mv.visitEnd();
      }
      cw.visitEnd();

      return cw.toByteArray();
  }
}
