package com.github.zhgzhg.tinydi.components;

/**
 * Interface marker for components which should be executed immediately after the whole dependency injection preparation process is done.
 * If more than one such components are found, all of them will be executed consequently in undefined order.
 */
public interface EntryPoint extends Runnable {
}
