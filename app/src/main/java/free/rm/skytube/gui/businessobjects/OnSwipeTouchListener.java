/*
 * SkyTube
 * Copyright (C) 2017  Ramon Mifsud
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package free.rm.skytube.gui.businessobjects;

import android.content.Context;
import android.graphics.Rect;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.view.GestureDetectorCompat;

/**
 * Detects user's swipe motions and taps.
 */
public abstract class OnSwipeTouchListener implements View.OnTouchListener {

	private final GestureDetectorCompat gestureDetector;
	private final GestureListener gestureListener;


	protected OnSwipeTouchListener(Context context){
		gestureListener = new GestureListener();
		gestureDetector = new GestureDetectorCompat(context, gestureListener);
	}


	@Override
	public boolean onTouch(View v, MotionEvent event) {
		gestureListener.onTouchEvent(event);
		return true;
	}


	/**
	 * User swiped to the right.
	 *
	 * @return True if the event was consumed.
	 */
	public boolean onSwipeRight() {
		return false;
	}


	/**
	 * User swiped to the left from the right side of the view with a width == 20%.
	 *
	 * @return True if the event was consumed.
	 */
	public boolean onSwipeLeft() {
		return false;
	}


	/**
	 * User swiped to the top from the bottom side of the view with a height == 20%.
	 *
	 * @return True if the event was consumed.
	 */
	public boolean onSwipeTop() {
		return false;
	}


	/**
	 * User swiped to the bottom.
	 *
	 * @return True if the event was consumed.
	 */
	public boolean onSwipeBottom() {
		return false;
	}


	/**
	 * User double tapped. The X coordinate of the tap (relative to the view) is passed
	 * so the handler can tell the left side from the right side.
	 *
	 * @param tapX X coordinate of the double tap, relative to the view.
	 * @return True if the event was consumed.
	 */
	public boolean onDoubleTap(float tapX) {
		return false;
	}


	/**
	 * User single tapped.
	 *
	 * @return True if the event was consumed.
	 */
	public boolean onSingleTap() {
		return false;
	}


	/**
	 * Called every time any gesture is ended.
	 */
	public abstract void onGestureDone();


	/**
	 * User swiped from top to bottom or from bottom to top at the left side of the view.
	 */
	public abstract void adjustVolumeLevel(double adjustPercent);


	/**
	 * User swiped from left to right or from right to left at any place of the view except 20% from the right.
	 */
	public abstract void adjustVideoPosition(double adjustPercent, boolean forwardDirection);


	/**
	 * In touch listener we don't know the rect of the view. This method should be overridden and should return actual view rect (because rect changes when orientation changes).
	 *
	 */
	public abstract Rect viewRect();


	////////////////////////////////////////////////////////////////////////////////////////////////

	private enum SwipeEventType {
		NONE,
		VOLUME,
		SEEK,
		COMMENTS,
		DESCRIPTION
	}


	private final class GestureListener extends GestureDetector.SimpleOnGestureListener {

		private static final int SWIPE_THRESHOLD = 50;
		private SwipeEventType swipeEventType = SwipeEventType.NONE;
		private MotionEvent startEvent;

		@Override
		public boolean onDoubleTap(MotionEvent e) {
			return OnSwipeTouchListener.this.onDoubleTap(e.getX());
		}


		@Override
		public boolean onSingleTapConfirmed(MotionEvent e) {
			return onSingleTap();
		}


		@Override
		public boolean onScroll(MotionEvent start, MotionEvent end,
								float distanceX, float distanceY) {
			// function depends on start position. (volume or play position.)
			double yDistance = end.getY() - start.getY();
			double xDistance = end.getX() - start.getX();

			if (swipeEventType == SwipeEventType.COMMENTS) {
				return onSwipeLeft();
			} else if (swipeEventType == SwipeEventType.DESCRIPTION) {
				return onSwipeTop();
			} else if (swipeEventType == SwipeEventType.VOLUME) {
				// use half of volumeRect's height to calculate percent.
				double percent = yDistance / (getVolumeRect().height() / 2.0f) * -1.0f;
				adjustVolumeLevel(percent);
			} else if (swipeEventType == SwipeEventType.SEEK) {
				double percent = xDistance / viewRect().width();
				adjustVideoPosition(percent, distanceX < 0);
			}

			return true;
		}

		private boolean isEventValid(MotionEvent event) {
			// this gesture just worked on single pointer.
			return event.getPointerCount() == 1;
		}

		private void onTouchEvent(MotionEvent event) {
			if (isEventValid(event)) {
				// decide which process is needed.
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						startEvent = MotionEvent.obtain(event);
						break;
					case MotionEvent.ACTION_MOVE:
						if (swipeEventType == SwipeEventType.NONE && startEvent != null) {
							swipeEventType = whatTypeIsIt(startEvent, event);
						}
						break;
					case MotionEvent.ACTION_UP:
						onGestureDone();
						swipeEventType = SwipeEventType.NONE;
						startEvent = null;
						break;
					default:
						break;
				}
			}

			gestureDetector.onTouchEvent(event);
		}


		/**
		 * Ok, swipe detected. What did user want? Let's find out
		 */
		private SwipeEventType whatTypeIsIt(MotionEvent startEvent, MotionEvent currentEvent) {
			float startX = startEvent.getX();
			float startY = startEvent.getY();
			float currentX = currentEvent.getX();
			float currentY = currentEvent.getY();
			float diffY = startY - currentY;
			float diffX = startX - currentX;

			if (Math.abs(currentX - startX) >= SWIPE_THRESHOLD && Math.abs(currentX - startX) > Math.abs(currentY - startY)) {
				if (getCommentsRect().contains((int) startX, (int) startY) && diffX > 0)
					return SwipeEventType.COMMENTS;

				return SwipeEventType.SEEK;
			} else if (Math.abs(currentY - startY) >= SWIPE_THRESHOLD) {
				if (getDescriptionRect().contains((int) startX, (int) startY) && diffY > 0) {
					return SwipeEventType.DESCRIPTION;
				} else if (getVolumeRect().contains((int) startX, (int) startY)) {
					// Volume is always on the left half of the screen.
					return SwipeEventType.VOLUME;
				}
				// A vertical swipe on the right half does nothing (brightness control was removed).
			}
			return SwipeEventType.NONE;
		}


		/**
		 * Here we decide in what place of the screen user should swipe to get a new volume value.
		 * Volume is always on the left half of the screen.
		 */
		private Rect getVolumeRect() {
			return new Rect(0, 0, viewRect().right / 2, viewRect().bottom);
		}


		/**
		 * Here we choose a rect for swipe which then will be used to open the comments view.
		 */
		private Rect getCommentsRect() {
			// 20% from right side will trigger comments view
			return new Rect((int)(viewRect().right - Math.min(viewRect().bottom, viewRect().right) * 0.2), 0, viewRect().right, viewRect().bottom);
		}

		/**
		 * Here we choose a rect for swipe which then will be used to open the description view.
		 */
		private Rect getDescriptionRect() {
			// 20% from bottom side will trigger description view
			return new Rect(0, (int)(viewRect().bottom - Math.min(viewRect().bottom, viewRect().right) * 0.2), viewRect().right, viewRect().bottom);
		}

	}

}