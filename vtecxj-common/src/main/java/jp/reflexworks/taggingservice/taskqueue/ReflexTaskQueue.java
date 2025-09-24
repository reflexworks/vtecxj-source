package jp.reflexworks.taggingservice.taskqueue;

import java.util.concurrent.Callable;

/**
 * Reflex非同期処理.
 * @param <T> 非同期処理の戻り型
 */
public interface ReflexTaskQueue<T> extends Callable<T> {

}
