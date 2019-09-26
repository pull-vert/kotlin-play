package coroutines

/**
 * acts as a [java.util.concurrent.Flow.Publisher]
 */
@FunctionalInterface
interface Coroutine<T> {
    /**
     * Adds the given Subscriber if possible.  If already
     * subscribed, or the attempt to subscribe fails due to policy
     * violations or errors, the Subscriber's {@code onError}
     * method is invoked with an {@link IllegalStateException}.
     * Otherwise, the Subscriber's {@code onSubscribe} method is
     * invoked with a new {@link Subscription}.  Subscribers may
     * enable receiving items by invoking the {@code request}
     * method of this Subscription, and may unsubscribe by
     * invoking its {@code cancel} method.
     *
     * @param subscriber the subscriber
     * @throws NullPointerException if subscriber is null
     */
    fun subscribe(subscriber: CoroutineSubscriber<in T>)
}

/**
 * A receiver of messages.  The methods in this interface are
 * invoked in strict sequential order for each {@link
 * Subscription}.
 *
 * acts as a [java.util.concurrent.Flow.Subscriber]
 *
 * @param <T> the subscribed item type
 */
interface CoroutineSubscriber<T> {
    /**
     * Method invoked prior to invoking any other Subscriber
     * methods for the given Subscription. If this method throws
     * an exception, resulting behavior is not guaranteed, but may
     * cause the Subscription not to be established or to be cancelled.
     *
     *
     * Typically, implementations of this method invoke `subscription.start` to enable receiving item.
     *
     * @param subscription a new subscription
     */
    fun onSubscribe(subscription: CoroutineSubscription)

    /**
     * Method invoked with a Subscription's item.  If this
     * method throws an exception, resulting behavior is not
     * guaranteed, but may cause the Subscription to be cancelled.
     *
     * @param item the item
     */
    fun onValue(item: T)

    /**
     * Method invoked upon an unrecoverable error encountered by a
     * Coroutine or Subscription, after which no other Subscriber
     * methods are invoked by the Subscription.  If this method
     * itself throws an exception, resulting behavior is
     * undefined.
     *
     * @param throwable the exception
     */
    fun onError(throwable: Throwable)

    /**
     * Method invoked when it is known that no additional
     * Subscriber method invocations will occur for a Subscription
     * that is not already terminated by error, after which no
     * other Subscriber methods are invoked by the Subscription.
     * If this method throws an exception, resulting behavior is
     * undefined.
     */
    fun onComplete()
}

/**
 * acts as a [java.util.concurrent.Flow.Subscription]
 */
interface CoroutineSubscription {
    fun start()

    fun cancel()
}
