package com.schlimm.bytecode.invokedynamic.generator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodType;

import org.objectweb.asm.MethodVisitor;

import com.schlimm.bytecode.invokedynamic.AbstractDynamicInvokerGenerator;

public class VarArgsParameterDynamicInvokerGenerator extends AbstractDynamicInvokerGenerator {

	@Override
	protected int addMethodParameters(MethodVisitor mv) {
		mv.visitInsn(ICONST_2);
		mv.visitTypeInsn(ANEWARRAY, "java/lang/String");
		mv.visitVarInsn(ASTORE, 0);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitInsn(ICONST_0);
		mv.visitLdcInsn("Hello");
		mv.visitInsn(AASTORE);
		mv.visitVarInsn(ALOAD, 0);
		mv.visitInsn(ICONST_1);
		mv.visitLdcInsn("World!");
		mv.visitInsn(AASTORE);
		mv.visitVarInsn(ALOAD, 0);
		return 3;
	}

	public static void main(String[] args) throws IOException, Exception {
		FileOutputStream fos = new FileOutputStream(new File("target/classes/com/schlimm/bytecode/VarArgsParameterDynamicInvoker.class"));
		fos.write(new VarArgsParameterDynamicInvokerGenerator().dump("com/schlimm/bytecode/VarArgsParameterDynamicInvoker", "com/schlimm/bytecode/invokedynamic/linkageclasses/VarArgsParameterDynamicLinkageExample", "bootstrapDynamic", MethodType.methodType(void.class, new Class[]{Object[].class}).toMethodDescriptorString()));
	}
	
}
