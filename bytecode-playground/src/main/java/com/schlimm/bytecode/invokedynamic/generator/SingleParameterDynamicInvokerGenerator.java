package com.schlimm.bytecode.invokedynamic.generator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodType;

import org.objectweb.asm.MethodVisitor;

import com.schlimm.bytecode.invokedynamic.AbstractDynamicInvokerGenerator;

public class SingleParameterDynamicInvokerGenerator extends AbstractDynamicInvokerGenerator {

	@Override
	protected int addMethodParameters(MethodVisitor mv) {
		mv.visitIntInsn(BIPUSH, 10);
		return 1;
	}

	public static void main(String[] args) throws IOException, Exception {
		FileOutputStream fos = new FileOutputStream(new File("target/classes/com/schlimm/bytecode/SingleParameterDynamicInvoker.class"));
		fos.write(new SingleParameterDynamicInvokerGenerator().dump("com/schlimm/bytecode/SingleParameterDynamicInvoker", "com/schlimm/bytecode/invokedynamic/linkageclasses/SingleParameterDynamicLinkageExample", "bootstrapDynamic", MethodType.methodType(void.class, new Class[]{int.class}).toMethodDescriptorString()));
	}
	
}
