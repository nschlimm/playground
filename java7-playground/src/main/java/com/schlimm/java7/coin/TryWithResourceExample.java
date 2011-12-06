package com.schlimm.java7.coin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class TryWithResourceExample {

	public static void main(String[] args) throws FileNotFoundException {
		
		// Java 7 try-with-resource
		
		String file1 = "TryWithResourceFile.out";
		try (OutputStream out = new FileOutputStream(file1)) {
			out.write("Some silly file content ...".getBytes());
			":-p".charAt(3);
		} catch (StringIndexOutOfBoundsException | IOException e) {
			System.out.println("Exception on operating file " + file1 + ": " + e.getMessage());
		}
		
		// Java 6 style
		
		String file2 = "WithoutTryWithResource.out";
		OutputStream out = new FileOutputStream(file2);
		try {
			out.write("Some silly file content ...".getBytes());
			":-p".charAt(3);
		} catch (StringIndexOutOfBoundsException | IOException e) {
			System.out.println("Exception on operating file " + file2 + ": " + e.getMessage());
		}

		// Let's try to operate on the resources
		
		File f1 = new File(file1);
		if (f1.delete())
			System.out.println("Successfully deleted: " + file1);
		else
			System.out.println("Problems deleting: " + file1);

		File f2 = new File(file2);
		if (f2.delete())
			System.out.println("Successfully deleted: " + file2);
		else
			System.out.println("Problems deleting: " + file2);
		
	}
}
