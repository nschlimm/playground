package com.schlimm.webappbenchmarker.command.std;

import java.util.Random;

public class Trials {

	public static void main(String[] args) {
		for (int i = 0; i < 100000; i++) {
			System.out.println(new Random().nextInt(100));
		}
	}
}
