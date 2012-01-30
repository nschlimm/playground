package com.schlimm.bytecode.invokedynamic.generator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.objectweb.asm.MethodVisitor;

import com.schlimm.bytecode.invokedynamic.AbstractDynamicInvokerGenerator;

public class SimpleDynamicInvokerGenerator extends AbstractDynamicInvokerGenerator {

	@Override
	protected int addMethodParameters(MethodVisitor mv) {
		return 0;
	}

	public static void main(String[] args) throws IOException, Exception {
		String dynamicInvokerClassName = "com/schlimm/bytecode/SimpleDynamicInvoker";
		FileOutputStream fos = new FileOutputStream(new File("target/classes/" + dynamicInvokerClassName + ".class"));
		fos.write(new SimpleDynamicInvokerGenerator().dump(dynamicInvokerClassName, "com/schlimm/bytecode/invokedynamic/linkageclasses/SimpleDynamicLinkageExample", "bootstrapDynamic", "()V"));
	}
	
}
