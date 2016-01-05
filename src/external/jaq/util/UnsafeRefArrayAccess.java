package external.jaq.util;

import static external.jaq.util.UnsafeAccess.UNSAFE;

/**
 * A concurrent access enabling class used by circular array based queues this class exposes an offset computation
 * method along with differently memory fenced load/store methods into the underlying array. The class is pre-padded and
 * the array is padded on either side to help with False sharing prvention. It is expected theat subclasses handle post
 * padding.
 * <p>
 * Offset calculation is separate from access to enable the reuse of a give compute offset.
 * <p>
 * Load/Store methods using a <i>buffer</i> parameter are provided to allow the prevention of final field reload after a
 * LoadLoad barrier.
 * <p>
 *
 * @author nitsanw
 *
 * @param
 */
public final class UnsafeRefArrayAccess {

	private UnsafeRefArrayAccess() {
	}

	/**
	 * A plain store (no ordering/fences) of an element to a given offset
	 *
	 * @param buffer this.buffer
	 * @param offset computed via { UnsafeRefArrayAccess#calcElementOffset(long)}
	 * @param e an orderly kitty
	 */
	public static final <E> void spElement(E[] buffer, long offset, E e) {
		UNSAFE.putObject(buffer, offset, e);
	}

	/**
	 * An ordered store(store + StoreStore barrier) of an element to a given offset
	 *
	 * @param buffer this.buffer
	 * @param offset computed via { UnsafeRefArrayAccess#calcElementOffset(long)}
	 * @param e an orderly kitty
	 */
	public static final <E> void soElement(E[] buffer, long offset, E e) {
		UNSAFE.putOrderedObject(buffer, offset, e);
	}

	/**
	 * A plain load (no ordering/fences) of an element from a given offset.
	 *
	 * @param buffer this.buffer
	 * @param offset computed via { UnsafeRefArrayAccess#calcElementOffset(long)}
	 * @return the element at the offset
	 */
	@SuppressWarnings("unchecked")
	public static final <E> E lpElement(E[] buffer, long offset) {
		return (E) UNSAFE.getObject(buffer, offset);
	}

	/**
	 * A volatile load (load + LoadLoad barrier) of an element from a given offset.
	 *
	 * @param buffer this.buffer
	 * @param offset computed via { UnsafeRefArrayAccess#calcElementOffset(long)}
	 * @return the element at the offset
	 */
	@SuppressWarnings("unchecked")
	public static final <E> E lvElement(E[] buffer, long offset) {
		return (E) UNSAFE.getObjectVolatile(buffer, offset);
	}
}