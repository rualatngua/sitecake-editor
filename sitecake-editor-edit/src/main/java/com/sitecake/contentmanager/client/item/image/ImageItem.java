package com.sitecake.contentmanager.client.item.image;

import com.allen_sauer.gwt.dnd.client.util.DOMUtil;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.ImageElement;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Float;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.DoubleClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.sitecake.commons.client.util.CSSStyleDeclaration;
import com.sitecake.commons.client.util.DomUtil;
import com.sitecake.contentmanager.client.EventBus;
import com.sitecake.contentmanager.client.GinInjector;
import com.sitecake.contentmanager.client.commons.Axis;
import com.sitecake.contentmanager.client.commons.Point;
import com.sitecake.contentmanager.client.commons.Rectangle;
import com.sitecake.contentmanager.client.event.EditCompleteEvent;
import com.sitecake.contentmanager.client.event.LinkEditorEvent;
import com.sitecake.contentmanager.client.item.ContentItem;
import com.sitecake.contentmanager.client.resources.EditorClientBundle;
import com.sitecake.contentmanager.client.toolbar.LinkableItem;

public class ImageItem extends ContentItem implements LinkableItem {

	public static final String CONTENT_TYPE_NAME = "IMAGE";
	
	interface ImageItemUiBinder extends UiBinder<Element, ImageItem> {}

	interface CssStyle extends CssResource {
		@ClassName("mode-resize")
		String modeResize();
		
		@ClassName("mode-crop")
		String modeCrop();
		
		@ClassName("mode-frame")
		String modeFrame();
		
		String working();
	}
	
	private static ImageItemUiBinder uiBinder = GWT.create(ImageItemUiBinder.class);
	
	public enum Mode {
		VIEW,
		RESIZE,
		CROP,
		FRAME
	}
	
	private class TransformationState {
		private Rectangle viewport;
		private Rectangle target;

		public Rectangle getViewport() {
			return viewport;
		}

		public Rectangle getTarget() {
			return target;
		}

		public void set(TransformationState state) {
			viewport.set(state.viewport);
			target.set(state.target);
		}
		
		public TransformationState(double width, double height,
				double targetWidth, double targetHeight, double offsetTop,
				double offsetLeft, double top, double left) {
			viewport = new Rectangle(new Point(left, top), width, height);
			target = new Rectangle(new Point(offsetLeft, offsetTop), targetWidth, targetHeight);
		}
		
		public TransformationState(Rectangle viewport, Rectangle target) {
			this.viewport = viewport.clone();
			this.target = target.clone();
		}
		
		public TransformationState clone() {
			TransformationState clone = new TransformationState(viewport, target);
			return clone;
		}
		
		public void resize(double dx, Axis axis, Rectangle outerLimit, Rectangle innerLimit, boolean swapPoints) {
			double dy = (dx * viewport.getHeight()) / viewport.getWidth();
			if ( axis.equals(Axis.NE) || axis.equals(Axis.SW) ) {
				dy = -dy;
			}
			resize(dx, dy, axis, outerLimit, innerLimit, swapPoints);
		}
		
		public void resize(double dx, double dy, Axis axis, Rectangle outerLimit, Rectangle innerLimit, boolean swapPoints) {
			
			double startWidth = viewport.getWidth();
			double startHeight = viewport.getHeight();
			
			viewport.resize(dx, dy, axis, swapPoints);
			
			if ( innerLimit != null ) {
				viewport.innerLimit(innerLimit, axis);
			}
			
			if ( outerLimit != null ) {
				viewport.outerLimit(outerLimit, false);
			}
			
			double newWidth = viewport.getWidth();
			double newHeight = viewport.getHeight();

			double widthRatio = newWidth / startWidth;
			double heightRatio = newHeight / startHeight;
			
			double targetNewWidth = widthRatio * target.getWidth();
			double targetNewHeight = heightRatio * target.getHeight();
			
			double targetNewOffsetLeft = widthRatio * target.getStart().getX();
			double targetNewOffsetTop = heightRatio * target.getStart().getY();

			viewport.setWidthHeight(newWidth, newHeight);
			target.getStart().setXY(targetNewOffsetLeft, targetNewOffsetTop);
			target.setWidthHeight(targetNewWidth, targetNewHeight);
		}
		
		public void resizeWidth(double newWidth, double ratio) {
			
			double width = viewport.getWidth();
			double height = viewport.getHeight();
			double newHeight = newWidth / ratio;

			double widthRatio = newWidth / width;
			double heightRatio = newHeight / height;
			
			double targetNewWidth = widthRatio * target.getWidth();
			double targetNewHeight = heightRatio * target.getHeight();
			
			double targetNewOffsetLeft = widthRatio * target.getStart().getX();
			double targetNewOffsetTop = heightRatio * target.getStart().getY();

			viewport.setWidthHeight(newWidth, newHeight);
			target.getStart().setXY(targetNewOffsetLeft, targetNewOffsetTop);
			target.setWidthHeight(targetNewWidth, targetNewHeight);
		}

		@Override
		public boolean equals(Object obj) {
			if ( obj == null || !(obj instanceof TransformationState) ) {
				return false;
			}
			
			if ( obj == this ) {
				return true;
			}

			TransformationState state = (TransformationState)obj;
			
			return ( viewport.equals(state.viewport) && target.equals(state.target) );
		}		
		
		
	}
	
	private Rectangle RESIZE_MIN_RECT = new Rectangle(new Point(0, 0), 50, 30);
	
	private Rectangle RESIZE_MAX_RECT = new Rectangle(new Point(0, 0), 1, 1);
	
	private Mode mode;

	/**
	 * State used in VIEW mode. Not changed during the edition, only at the end
	 * if the edition is not canceled.
	 */
	private TransformationState finalState;
	
	/**
	 * Represents the last confirmed edited state (at the end of any edit sub-phase).
	 * Used at the start of each new edit sub-phase.
	 */
	private TransformationState confirmedState;
	
	/**
	 * The item's width/height are bound to this state.
	 */
	private TransformationState currentState;
	
	/**
	 * A state used to define frame properties in CROP and FRAME modes.
	 */
	private TransformationState workingState;
	
	/**
	 * A state used as a helper in sub-phase operations.
	 */
	private TransformationState tmpState;
	
	private ImageObject sourceImageObject;
	
	private double imageRatio;
	
	private String link = "";
	
	private String linkOrig;
	
	private String description;
	
	private String origTranformation;
	
	private EventBus eventBus = GinInjector.instance.getEventBus();
	
	@UiField
	CssStyle cssStyle;
	
	@UiField
	Element styleContainer;
	
	@UiField
	Element backPlane;
	
	@UiField
	Element curtain;
	
	@UiField
	Element modeDisplay;
	
	@UiField
	Element frontPlane;
	
	@UiField
	Element frame;
	
	@UiField
	Element handleNW;
	
	@UiField
	Element handleNE;
	
	@UiField
	Element handleSW;
	
	@UiField
	Element handleSE;
	
	@UiField
	Element handleN;
	
	@UiField
	Element handleS;
	
	@UiField
	Element handleE;
	
	@UiField
	Element handleW;
	
	@UiField
	Element selectOverlay;
	
	private Point mousePosStart;
	
	private Point mousePosCurrent;
	
	private double dx;
	
	private double dy;
	
	private boolean mouseDown;
	
	private boolean mouseMoved;
	
	private boolean modified;
	
	private Axis dragAxis;
	
	protected boolean dirty = false;
	
	public static ImageItem create(ImageObject image) {
		ImageItem item = GWT.create(ImageItem.class);
		item.init(image);
		return item;
	}
	
	protected ImageItem() {
		super();
	}

	@Override
	public String getContentTypeName() {
		return CONTENT_TYPE_NAME;
	}
	
	public void init(Element element) {
		ImageObject imageObject = new ImageObject();
		
		ImageElement imgElement = element.cast();
		if ( element.getTagName().toLowerCase().equals("a") ) {
			link = element.getAttribute("href");
			imgElement = element.getFirstChildElement().cast();
		}

		style = imgElement.getClassName();
		
		//TODO: extract and set imageObject.id
		imageObject.setResizedUrl(imgElement.getSrc());
		imageObject.setResizedWidth(Integer.valueOf(imgElement.getWidth()));
		imageObject.setResizedHeight(Integer.valueOf(imgElement.getHeight()));
		
		description = imgElement.getAttribute("alt");
		
		if ( imgElement.hasAttribute("data") ) {
			origTranformation = imgElement.getAttribute("data");
		}
		
		init(imageObject, element);
	}

	public void init(ImageObject imageObject) {
		description = "";
		
		init(imageObject, null);
	}
	
	private void init(ImageObject imageObject, Element element) {

		sourceImageObject = imageObject.clone();
		imageRatio = ((double)sourceImageObject.getResizedWidth()) / sourceImageObject.getResizedHeight(); 
		
		if ( origTranformation == null ) {
			origTranformation = sourceImageObject.getResizedWidth() + ":" + sourceImageObject.getResizedHeight() + ":" +
					0 + ":" + 0 + ":" +
					sourceImageObject.getResizedWidth() + ":" + sourceImageObject.getResizedHeight();
		}
		
		finalState = new TransformationState(
				imageObject.getResizedWidth(),
				imageObject.getResizedHeight(),
				imageObject.getResizedWidth(),
				imageObject.getResizedHeight(), 0, 0, 0, 0);
		
		confirmedState = finalState.clone();
		currentState = finalState.clone();
		workingState = finalState.clone();
		tmpState = finalState.clone();
		
		Element newElement = uiBinder.createAndBindUi(this);
		if ( element != null ) {
			DomUtil.replaceElement(element, newElement);
		}
		setElement(newElement);
		setContainerElementStyle();
		
		ImageElement img = DOM.createImg().<ImageElement>cast();
		img.setSrc(sourceImageObject.getResizedUrl());
		img.setWidth(sourceImageObject.getResizedWidth());
		img.setHeight(sourceImageObject.getResizedHeight());
		
		frontPlane.appendChild(img);
		backPlane.appendChild(img.cloneNode(false));
		
		setMode(Mode.VIEW);
		
		mousePosStart = new Point(0, 0);
		mousePosCurrent = new Point(0, 0);	
		
		initHandlers();
		mouseMoved = false;
	}
	
	private void cloneInit() {
		Element newElement = uiBinder.createAndBindUi(this);
		setElement(newElement);
		setContainerElementStyle();
		
		ImageElement img = DOM.createImg().<ImageElement>cast();
		img.setSrc(sourceImageObject.getResizedUrl());
		img.setWidth(sourceImageObject.getResizedWidth());
		img.setHeight(sourceImageObject.getResizedHeight());
		
		frontPlane.appendChild(img);
		backPlane.appendChild(img.cloneNode(false));
		
		setMode(Mode.VIEW);
		
		mousePosStart = new Point(0, 0);
		mousePosCurrent = new Point(0, 0);	
		
		initHandlers();
		mouseMoved = false;
	}
	
	@Override
	public ContentItem cloneItem() {
		ImageItem clone = new ImageItem();
		cloneItem(clone);
		clone.description = description;
		clone.link = link;
		//clone.init(sourceImageObject, null);

		clone.sourceImageObject = sourceImageObject.clone();
		clone.imageRatio = imageRatio; 
		
		clone.finalState = finalState.clone();
		clone.confirmedState = confirmedState.clone();
		clone.currentState = currentState.clone();
		clone.workingState = workingState.clone();
		clone.tmpState = tmpState.clone();
		clone.origTranformation = origTranformation;
		
		clone.cloneInit();
		
		return clone;
	}

	@Override
	public String getHtml() {
		String[] dataParts = origTranformation.split(":");
		double origSrcWidth = Double.parseDouble(dataParts[0]);
		double origSrcHeight = Double.parseDouble(dataParts[1]);
		double origSrcX = Double.parseDouble(dataParts[2]);
		double origSrcY = Double.parseDouble(dataParts[3]);
		double origDstWidth = Double.parseDouble(dataParts[4]);
		double origDstHeight = Double.parseDouble(dataParts[5]);
		
		double xCorrection = finalState.getTarget().getWidth() / origDstWidth;
		double yCorrection = finalState.getTarget().getHeight() / origDstHeight;
		
		NumberFormat numberFormat = NumberFormat.getFormat("######");
		
		String srcWidth = numberFormat.format( xCorrection * origSrcWidth );
		String srcHeight = numberFormat.format( yCorrection * origSrcHeight );
		
		String srcX = numberFormat.format( xCorrection * origSrcX + Math.abs( finalState.getTarget().getStart().getX() ) );
		String srcY = numberFormat.format( yCorrection * origSrcY + Math.abs( finalState.getTarget().getStart().getY() ) );

		String dstWidth = numberFormat.format(finalState.getViewport().getWidth());
		String dstHeight = numberFormat.format(finalState.getViewport().getHeight());
		
		String data = srcWidth + ":" + srcHeight + ":" +
			srcX + ":" + srcY + ":" +
			dstWidth + ":" + dstHeight;
			
		String html = "<img alt=\"" + description + 
			"\" width=\"" + dstWidth + 
			"\" height=\"" + dstHeight + 
			"\" class=\"" + style + 
			"\" src=\"" + sourceImageObject.getResizedUrl() + 
			"\" data=\"" + data + "\"/>";
		
		if ( !"".equals(link) ) {
			html = "<a href=\"" + link + "\">" + html + "</a>";
		}
		
		return html;
	}

	@Override
	public boolean isEditable() {
		return true;
	}
	
	@Override
	public void onInclusion() {
		super.onInclusion();
		// TODO: check why is this called three times on every drag/drop
		setContainerElementStyle();
		
		int maxWidth = getMaxWidth();
		
		if ( finalState.getViewport().getWidth() > maxWidth ) {
			finalState.resizeWidth(maxWidth, imageRatio);
			refresh();
		}
	}

	@Override
	public void setSelected(boolean select) {
		if ( select && !selected ) {
			selected = true;
			selectOverlay.getStyle().setDisplay(Display.BLOCK);
		} else if ( !select && selected ){
			selected = false;
			selectOverlay.getStyle().setDisplay(Display.NONE);
		}
	}

	@Override
	public void setStyle(String style) {
		if ( !this.style.equals(style) ) {
			dirty = true;
		}
		super.setStyle(style);
	}

	@Override
	public void startEditing(String mode) {
		if ( !edited ) {
			setSelected(false);
			edited = true;
			confirmedState.set(finalState);
			
			linkOrig = link;
			eventBus.fireEventDeferred(new LinkEditorEvent(link, false));
			
			setMode(Mode.RESIZE);
		}
		dirty = false;
	}

	@Override
	public boolean stopEditing(boolean cancel) {
		
		if ( !edited && !dirty ) {
			return false;
		}
		
		eventBus.fireEventDeferred(new LinkEditorEvent("", true));		
		
		if ( cancel ) {
			link = linkOrig;
		} else {
			dirty = dirty || !confirmedState.equals(finalState);
			finalState.set(confirmedState);
			
			dirty = dirty || !link.equals(linkOrig);
		}
		
		if ( super.stopEditing(cancel) ) {
			dirty = true;
		}
		
		setMode(Mode.VIEW);
		DOM.releaseCapture(getElement());
		
		return dirty;
	}

	@Override
	public Rectangle getContenxtRect() {
		contextRect.getStart().setX(styleContainer.getAbsoluteLeft() - 1);
		contextRect.getStart().setY(styleContainer.getAbsoluteTop() - 1);
		contextRect.setWidth(styleContainer.getOffsetWidth() + 2);
		contextRect.setHeight(styleContainer.getOffsetHeight() + 2);
		
		return contextRect;
	}

	private void initHandlers() {
		addDomHandler(new MouseDownHandler() {
			public void onMouseDown(MouseDownEvent event) {
				mouseDown(event);
			}
		}, MouseDownEvent.getType());

		addDomHandler(new MouseUpHandler() {
			public void onMouseUp(MouseUpEvent event) {
				mouseUp(event);
			}
		}, MouseUpEvent.getType());
		
		addDomHandler(new MouseMoveHandler() {
			public void onMouseMove(MouseMoveEvent event) {
				mouseMove(event);
			}
		}, MouseMoveEvent.getType());
		
		addDomHandler(new DoubleClickHandler() {
			public void onDoubleClick(DoubleClickEvent event) {
				mouseDoubleClick(event);
			}
		}, DoubleClickEvent.getType());
		
		addDomHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				mouseClick(event);
			}
		}, ClickEvent.getType());
		
		addDomHandler(new KeyDownHandler() {
			public void onKeyDown(KeyDownEvent event) {
				keyDown(event);
			}
		}, KeyDownEvent.getType());		
	}
	
	private void mouseDown(MouseDownEvent event) {
		if ( !edited ) {
			event.stopPropagation();
			event.preventDefault();
			return;
		}
		
		mouseMoved = false;
		if ( !mouseDown && shouldCapture(event) ) {
			event.stopPropagation();
			mouseDown = true;
			dragAxis = getDragAxis(event.getNativeEvent().getEventTarget());
			
			int x = event.getClientX() + Window.getScrollLeft();
			int y = event.getClientY() + Window.getScrollTop();
			mousePosStart.setXY(x, y);
			DOM.setCapture(getElement());
		}
		event.preventDefault();
	}
	
	private void mouseUp(MouseUpEvent event) {
		if ( !edited ) {
			event.stopPropagation();
			event.preventDefault();
			return;
		}
		
		if ( mouseDown ) {
			mouseDown = false;
			DOM.releaseCapture(getElement());
			if ( mouseMoved ) {
				dragStop();
			}
		}
	}
	
	private void mouseMove(MouseMoveEvent event) {
		if ( !edited ) {
			event.stopPropagation();
			event.preventDefault();
			return;
		}
		
		if ( mouseDown ) {
			int x = event.getClientX() + Window.getScrollLeft();
			int y = event.getClientY() + Window.getScrollTop();
			mousePosCurrent.setXY(x, y);
			dx = mousePosCurrent.getX() - mousePosStart.getX();
			dy = mousePosCurrent.getY() - mousePosStart.getY();

			if ( !mouseMoved ) {
				mouseMoved = true;
				DOMUtil.cancelAllDocumentSelections();
				dragStart();
			} else {
				dragMove();
			}
		
		}
		DOM.eventPreventDefault(DOM.eventGetCurrentEvent());
	}
	
	private void mouseClick(ClickEvent event) {
		if ( !edited ) {
			event.stopPropagation();
			event.preventDefault();
			return;
		}
		
		if ( mouseMoved ) {
			mouseMoved = false;
			return;
		}
		
		if ( !modified ) {
			setMode(getNextEditMode());
		}
	}
	
	private void mouseDoubleClick(DoubleClickEvent event) {
		if ( !edited ) {
			event.stopPropagation();
			event.preventDefault();
			return;
		}
		
		if ( modified ) {
			modified = false;
			if ( mode.equals(Mode.FRAME) ||
					( mode.equals(Mode.CROP) && frame.equals(event.getNativeEvent().getEventTarget()) ) ) {
				confirm();
			}
		}
	}
	
	private void keyDown(KeyDownEvent event) {
		if ( !edited ) {
			return;
		}
		
		if ( event.getNativeKeyCode() == KeyCodes.KEY_ENTER ) {
			confirm();
		} else if ( event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE ) {
			cancel();
		}
	}
	
	private boolean shouldCapture(MouseDownEvent event) {
		boolean result = false;
		
		switch ( mode ) {
			case RESIZE:
				EventTarget eventTarget = event.getNativeEvent().getEventTarget();
				result = ( handleNW.equals(eventTarget) || handleNE.equals(eventTarget) || 
					handleSW.equals(eventTarget) || handleSE.equals(eventTarget) );
				break;
	
			case CROP:
			case FRAME:
				result = true;
				break;
		}
		
		return result;
	}
	
	private Axis getDragAxis(EventTarget eventTarget) {
		Axis axis = Axis.NONE;
		
		if ( handleNW.equals(eventTarget) ) {
			axis = Axis.NW;
		} else if ( handleNE.equals(eventTarget) ) {
			axis = Axis.NE;
		} else if ( handleSW.equals(eventTarget) ) {
			axis = Axis.SW;
		} else if ( handleSE.equals(eventTarget) ) {
			axis = Axis.SE;
		} else if ( handleN.equals(eventTarget) ) {
			axis = Axis.N;
		} else if ( handleW.equals(eventTarget) ) {
			axis = Axis.W;
		} else if ( handleS.equals(eventTarget) ) {
			axis = Axis.S;
		} else if ( handleE.equals(eventTarget) ) {
			axis = Axis.E;
		} else if ( frame.equals(eventTarget) ) {
			axis = Axis.PAN;
		}

		return axis;
	}
	
	private void dragStart() {
		switch ( mode ) {
			case RESIZE:
				dragStartResize();
				break;
	
			case CROP:
				dragStartCrop();
				break;
	
			case FRAME:
				dragStartFrame();
				break;
		}			
	}
	
	private void dragStop() {
		switch ( mode ) {
			case RESIZE:
				dragStopResize();
				break;
	
			case CROP:
				dragStopCrop();
				break;
	
			case FRAME:
				dragStopFrame();
				break;
		}			
	}
	
	private void dragMove() {
		switch ( mode ) {
			case RESIZE:
				dragMoveResize();
				break;
	
			case CROP:
				dragMoveCrop();
				break;
	
			case FRAME:
				dragMoveFrame();
				break;
		}			
	}
	
	private void dragStartResize() {
		getElement().addClassName(cssStyle.working());
	}
	
	private void dragStartCrop() {
		modified = true;
		
		if ( dragAxis.equals(Axis.NONE) ) {
			dragAxis = Axis.SE;
			Point clickPoint = new Point(mousePosStart.getX() - styleContainer.getAbsoluteLeft(), 
					mousePosStart.getY() - styleContainer.getAbsoluteTop());
			
			workingState.getViewport().setPoints(clickPoint, clickPoint.add(dx, dy));
			workingState.resize(0, 0, dragAxis, currentState.getViewport(), null, true);
		}
		
		workingState.getTarget().getStart().setX(currentState.getTarget().getStart().getX() - workingState.getViewport().getStart().getX());
		workingState.getTarget().getStart().setY(currentState.getTarget().getStart().getY() - workingState.getViewport().getStart().getY());
		workingState.getTarget().setWidth(currentState.getTarget().getWidth());
		workingState.getTarget().setHeight(currentState.getTarget().getHeight());
		
		tmpState.set(workingState);
		frame.getStyle().clearDisplay();
		curtain.getStyle().clearDisplay();
		modeDisplay.getStyle().setDisplay(Display.NONE);
		
		getElement().addClassName(cssStyle.working());
	}
	
	private void dragStartFrame() {
		modified = true;
		tmpState.set(workingState);
		getElement().addClassName(cssStyle.working());
		modeDisplay.getStyle().setDisplay(Display.NONE);
	}
	
	private void dragStopResize() {
		modified = false;
		confirmedState.set(currentState);
		getElement().removeClassName(cssStyle.working());
		
		eventBus.fireEventDeferred(new EditCompleteEvent());		
	}
	
	private void dragStopCrop() {
		getElement().removeClassName(cssStyle.working());
	}
	
	private void dragStopFrame() {
		getElement().removeClassName(cssStyle.working());
	}
	
	private void dragMoveResize() {
		
		if ( dragAxis.equals(Axis.NW) || dragAxis.equals(Axis.SW) ) {
			dx = -dx;
		}
		
		currentState.set(confirmedState);
		
		RESIZE_MAX_RECT.setWidth(getMaxWidth());
		RESIZE_MAX_RECT.setHeight(RESIZE_MAX_RECT.getWidth() / imageRatio);
		
		currentState.resize(dx, Axis.SE, RESIZE_MAX_RECT, RESIZE_MIN_RECT, false);
		
		refresh();
	}
	
	private void dragMoveCrop() {
		
		workingState.set(tmpState);
		if ( dragAxis.equals(Axis.PAN) ) {
			workingState.getViewport().translate(dx, dy, currentState.getViewport(), null);
		} else {
			workingState.resize(dx, dy, dragAxis, currentState.getViewport(), null, true);			
		}

		workingState.getTarget().getStart().setX(currentState.getTarget().getStart().getX() - workingState.getViewport().getStart().getX());
		workingState.getTarget().getStart().setY(currentState.getTarget().getStart().getY() - workingState.getViewport().getStart().getY());
		workingState.getTarget().setWidth(currentState.getTarget().getWidth());
		workingState.getTarget().setHeight(currentState.getTarget().getHeight());
	
		refresh();
	}
	
	private void dragMoveFrame() {
		
		workingState.set(tmpState);
		if ( dragAxis.equals(Axis.PAN) ) {
			workingState.getViewport().translate(dx, dy, null, currentState.getViewport());
		} else {
			workingState.resize(dx, dragAxis, null, currentState.getViewport(), false);			
		}
		
		currentState.getTarget().getStart().setX(workingState.getTarget().getStart().getX() + workingState.getViewport().getStart().getX());
		currentState.getTarget().getStart().setY(workingState.getTarget().getStart().getY() + workingState.getViewport().getStart().getY());
		currentState.getTarget().setWidth(workingState.getTarget().getWidth());
		currentState.getTarget().setHeight(workingState.getTarget().getHeight());
		refresh();
	}

	private void confirm() {
		switch ( mode ) {
			case CROP:
				confirmCrop();
				break;
	
			case FRAME:
				confirmFrame();
				break;
		}
		
		eventBus.fireEventDeferred(new EditCompleteEvent());
	}
	
	private void cancel() {
		switch ( mode ) {
			case CROP:
				cancelCrop();
				break;
	
			case FRAME:
				cancelFrame();
				break;
		}			
	}
	
	private void confirmCrop() {
		modified = false;
		workingState.getViewport().translate(0, 0);
		confirmedState.set(workingState);
		imageRatio = confirmedState.getViewport().getWidth() / confirmedState.getViewport().getHeight();
		setMode(Mode.CROP);
	}

	private void confirmFrame() {
		modified = false;
		confirmedState.set(currentState);
		setMode(Mode.FRAME);
	}
	
	private void cancelCrop() {
		modified = false;		
		setMode(Mode.CROP);
	}

	private void cancelFrame() {
		modified = false;
		setMode(Mode.FRAME);
	}
	
	private Mode getNextEditMode() {
		Mode nextMode = null;
		
		switch ( mode ) {
			case RESIZE:
				nextMode = Mode.CROP;
				break;
	
			case CROP:
				nextMode = Mode.FRAME;
				break;
	
			case FRAME:
				nextMode = Mode.RESIZE;
				break;
		}
		
		return nextMode;
	}
	
	private void setMode(Mode mode) {
		this.mode = mode;
		setCssClassMode();
		
		frame.getStyle().clearDisplay();
		curtain.getStyle().clearDisplay();
		modeDisplay.getStyle().clearDisplay();
		getElement().removeClassName(cssStyle.working());
		
		switch ( mode ) {
		case VIEW:
			enterViewMode();
			break;

		case RESIZE:
			enterResizeMode();
			break;

		case CROP:
			enterCropMode();
			break;

		case FRAME:
			enterFrameMode();
			break;
		}
	}
	
	private void refresh() {
		
		switch ( mode ) {
		case VIEW:
			refreshViewMode();
			break;

		case RESIZE:
			refreshResizeMode();
			break;

		case CROP:
			refreshCropMode();
			break;

		case FRAME:
			refreshFrameMode();
			break;
		}
	}
	
	private void refreshViewMode() {

		getElement().getStyle().setWidth(finalState.getViewport().getWidth(), Unit.PX);
		getElement().getStyle().setHeight(finalState.getViewport().getHeight(), Unit.PX);
		
		setPlane(frontPlane, finalState);
	}
	
	private void refreshResizeMode() {
		
		getElement().getStyle().setWidth(currentState.getViewport().getWidth(), Unit.PX);
		getElement().getStyle().setHeight(currentState.getViewport().getHeight(), Unit.PX);

		setPlane(frontPlane, currentState);
		setFrame(currentState);
	}

	private void refreshCropMode() {
		setPlane(frontPlane, workingState);
		setPlane(backPlane, currentState);
		setFrame(workingState);
	}

	private void refreshFrameMode() {
		setPlane(frontPlane, currentState);
		setPlane(backPlane, workingState);
		setFrame(workingState);
	}

	private void enterViewMode() {
		refreshViewMode();
	}
	
	private void enterResizeMode() {
		currentState.set(confirmedState);
		refresh();
	}

	private void enterCropMode() {
		getElement().getStyle().setWidth(confirmedState.getViewport().getWidth(), Unit.PX);
		getElement().getStyle().setHeight(confirmedState.getViewport().getHeight(), Unit.PX);
		
		currentState.set(confirmedState);
		workingState.set(confirmedState);
		
		DomUtil.hideElement(frame);
		DomUtil.hideElement(curtain);
		
		refresh();
	}

	private void enterFrameMode() {
		currentState.set(confirmedState);
		workingState.set(confirmedState);
		refresh();
	}
	
	private void setCssClassMode() {
		String internalCssClassName = null;
		String externalCssClassName = null;
		
		switch ( mode ) {
			case RESIZE:
				internalCssClassName = cssStyle.modeResize();
				externalCssClassName = EditorClientBundle.INSTANCE.css().imageEditorModeResize();
				break;
	
			case CROP:
				internalCssClassName = cssStyle.modeCrop();
				externalCssClassName = EditorClientBundle.INSTANCE.css().imageEditorModeCrop();
				break;
	
			case FRAME:
				internalCssClassName = cssStyle.modeFrame();
				externalCssClassName = EditorClientBundle.INSTANCE.css().imageEditorModeFrame();
				break;
		}
		
		getElement().removeClassName(cssStyle.modeCrop());
		getElement().removeClassName(cssStyle.modeFrame());
		getElement().removeClassName(cssStyle.modeResize());
		
		getElement().removeClassName(EditorClientBundle.INSTANCE.css().imageEditorModeCrop());
		getElement().removeClassName(EditorClientBundle.INSTANCE.css().imageEditorModeFrame());
		getElement().removeClassName(EditorClientBundle.INSTANCE.css().imageEditorModeResize());
		
		if ( internalCssClassName != null ) {
			getElement().addClassName(internalCssClassName);
			getElement().addClassName(externalCssClassName);
		}
	}
	
	private void setPlane(Element plane, TransformationState state) {
		
		Style planeStyle = plane.getStyle();
		
		planeStyle.setWidth(state.getViewport().getWidth(), Unit.PX);
		planeStyle.setHeight(state.getViewport().getHeight(), Unit.PX);
		planeStyle.setTop(state.getViewport().getStart().getY(), Unit.PX);
		planeStyle.setLeft(state.getViewport().getStart().getX(), Unit.PX);

		ImageElement planeImg = plane.getFirstChildElement().<ImageElement>cast();
		com.google.gwt.dom.client.Style planeImgStyle = planeImg.getStyle();
		
		planeImgStyle.setWidth(state.getTarget().getWidth(), Unit.PX);
		planeImgStyle.setHeight(state.getTarget().getHeight(), Unit.PX);
		planeImgStyle.setTop(state.getTarget().getStart().getY(), Unit.PX);
		planeImgStyle.setLeft(state.getTarget().getStart().getX(), Unit.PX);
	}
	
	private void setFrame(TransformationState state) {
		Style style = frame.getStyle();

		style.setWidth(state.getViewport().getWidth(), Unit.PX);
		style.setHeight(state.getViewport().getHeight(), Unit.PX);
		style.setTop(state.getViewport().getStart().getY(), Unit.PX);
		style.setLeft(state.getViewport().getStart().getX(), Unit.PX);

		double left = ( state.getViewport().getWidth() - handleN.getClientWidth() ) / 2;
		double top = ( state.getViewport().getHeight() - handleW.getClientHeight() ) / 2;
		
		handleN.getStyle().setLeft(left, Unit.PX);
		handleS.getStyle().setLeft(left, Unit.PX);
		handleW.getStyle().setTop(top, Unit.PX);
		handleE.getStyle().setTop(top, Unit.PX);
	}
	
	private void adjustStyle(Element element) {
		Style style = getElement().getStyle();
		CSSStyleDeclaration srcStyle = CSSStyleDeclaration.get(element);
		
		copyStyle(srcStyle, "padding", "padding");
		copyStyle(srcStyle, "padding-top", "paddingTop");
		copyStyle(srcStyle, "padding-right", "paddingRight");
		copyStyle(srcStyle, "padding-bottom", "paddingBottom");
		copyStyle(srcStyle, "padding-left", "paddingLeft");

		copyStyle(srcStyle, "border", "border");
		
		copyStyle(srcStyle, "border-top", "borderTop");
		copyStyle(srcStyle, "border-right", "borderRight");
		copyStyle(srcStyle, "border-bottom", "borderBottom");
		copyStyle(srcStyle, "border-left", "borderLeft");
		
		copyStyle(srcStyle, "border-width", "borderWidth");
		copyStyle(srcStyle, "border-top-width", "borderTopWidth");
		copyStyle(srcStyle, "border-right-width", "borderRightWidth");
		copyStyle(srcStyle, "border-bottom-width", "borderBottomWidth");
		copyStyle(srcStyle, "border-left-width", "borderLeftWidth");

		copyStyle(srcStyle, "border-style", "borderStyle");
		copyStyle(srcStyle, "border-top-style", "borderTopStyle");
		copyStyle(srcStyle, "border-right-style", "borderRightStyle");
		copyStyle(srcStyle, "border-bottom-style", "borderBottomStyle");
		copyStyle(srcStyle, "border-left-style", "borderLeftStyle");

		copyStyle(srcStyle, "border-color", "borderColor");
		copyStyle(srcStyle, "border-top-color", "borderTopColor");
		copyStyle(srcStyle, "border-right-color", "borderRightColor");
		copyStyle(srcStyle, "border-bottom-color", "borderBottomColor");
		copyStyle(srcStyle, "border-left-color", "borderLeftColor");

		copyStyle(srcStyle, "margin", "margin");
		
		copyStyle(srcStyle, "margin-top", "marginTop");
		copyStyle(srcStyle, "margin-right", "marginRight");
		copyStyle(srcStyle, "margin-bottom", "marginBottom");
		copyStyle(srcStyle, "margin-left", "marginLeft");
		

		copyStyle(srcStyle, "top", "top");
		copyStyle(srcStyle, "left", "left");
		copyStyle(srcStyle, "right", "right");
		copyStyle(srcStyle, "bottom", "bottom");
		
		// for some reason, the next construct (setting float) does not work
		//copyStyle(srcStyle, "float", "float");
		// so, do it manually
		String srcFloat = srcStyle.getPropertyValue("float");
		if ( "right".equalsIgnoreCase(srcFloat) ) {
			style.setFloat(Float.RIGHT);
		} else if ( "left".equalsIgnoreCase(srcFloat) ) {
			style.setFloat(Float.LEFT);
		} else {
			style.clearFloat();
		}
		
		String srcDisplay = srcStyle.getPropertyValue("display");
		String display = "block";
		
		if ( "inline".equalsIgnoreCase(srcDisplay) || "inline-block".equalsIgnoreCase(srcDisplay) ) {
			display = "inline-block";
		} else {
			display = "block";
		}
		style.setProperty("display", display);
		
	}
	
	private void copyStyle(CSSStyleDeclaration srcStyle, String srcProperty, String dstProperty) {
		Style dstStyle = getElement().getStyle();
		String srcValue = srcStyle.getPropertyValue(srcProperty);
		if ( srcValue != null && !"".equals(srcValue) ) {
			dstStyle.setProperty(dstProperty, srcValue);
		}
	}
	
	private void setContainerElementStyle() {
		if ( container != null ) {
			ImageElement img = DOM.createImg().cast();
			img.getStyle().setWidth(1, Unit.PX);
			img.getStyle().setHeight(1, Unit.PX);

			String cssStyleName = getStyle();
			if ( cssStyleName != null && !cssStyleName.equals("") ) {
				img.addClassName(cssStyleName);
			}
			
			container.getElement().appendChild(img);
			adjustStyle(img);
			img.removeFromParent();
			img = null;
		}
	}
	
	private int getMaxWidth() {
		Double maxWidth = 0.0;
		
		if ( container != null ) {
			maxWidth = CSSStyleDeclaration.get(container.getElement()).getPropertyValueDouble("width");
			CSSStyleDeclaration cssStyle = CSSStyleDeclaration.get(getElement());
			maxWidth -= cssStyle.getPropertyValueInt("margin-left");
			maxWidth -= cssStyle.getPropertyValueInt("margin-right");
			maxWidth -= cssStyle.getPropertyValueInt("padding-left");
			maxWidth -= cssStyle.getPropertyValueInt("padding-right");
			maxWidth -= cssStyle.getPropertyValueInt("border-left-width");
			maxWidth -= cssStyle.getPropertyValueInt("border-right-width");
		}
		
		return maxWidth.intValue();
	}

	@Override
	public void link(String url, boolean confirmed) {
		link = url;
		if ( confirmed ) {
			eventBus.fireEventDeferred(new LinkEditorEvent("", true));
			eventBus.fireEventDeferred(new EditCompleteEvent());
		}
	}

	@Override
	public void unlink() {
		link = "";
		eventBus.fireEventDeferred(new LinkEditorEvent("", true));
		eventBus.fireEventDeferred(new EditCompleteEvent());
	}
	
}