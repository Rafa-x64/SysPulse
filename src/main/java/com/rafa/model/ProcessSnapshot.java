package com.rafa.model;

public record ProcessSnapshot(int pid, int ppid, int threads, int priority, String state, String name, long memKb) {}
