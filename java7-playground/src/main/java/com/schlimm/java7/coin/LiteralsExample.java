package com.schlimm.java7.coin;

public class LiteralsExample {

	public static void main(String[] args) {
		System.out.println("With underscores: ");
		
		long creditCardNumber = 1234_5678_9012_3456L;
		long bytes = 0b11010010_01101001_10010100_10010010;
		
		System.out.println(creditCardNumber);
		System.out.println(bytes);
		
		System.out.println("Without underscores: ");
		
		creditCardNumber = 1234567890123456L;
		bytes = 0b11010010011010011001010010010010;
		
		System.out.println(creditCardNumber);
		System.out.println(bytes);
		
	}
}
