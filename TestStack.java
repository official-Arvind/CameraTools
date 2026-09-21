package io.github.official_arvind.cameratools;

public class TestStack {
    public static void main(String[] args) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            System.out.println(element.getClassName());
        }
    }
}
