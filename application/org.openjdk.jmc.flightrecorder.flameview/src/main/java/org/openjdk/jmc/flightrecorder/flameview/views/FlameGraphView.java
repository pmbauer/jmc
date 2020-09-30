package org.openjdk.jmc.flightrecorder.flameview.views;

import java.io.IOException;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;
import org.openjdk.flightrecorder.stacktrace.tree.StacktraceTreeModel;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.util.StringToolkit;
import org.openjdk.jmc.flightrecorder.stacktrace.FrameSeparator;
import org.openjdk.jmc.flightrecorder.stacktrace.FrameSeparator.FrameCategorization;
import org.openjdk.jmc.flightrecorder.ui.FlightRecorderUI;
import org.openjdk.jmc.flightrecorder.util.Debouncer;
import org.openjdk.jmc.ui.common.util.AdapterUtil;
import org.openjdk.jmc.ui.misc.DisplayToolkit;

public class FlameGraphView extends ViewPart implements ISelectionListener {

	private Browser browser;
	private SashForm container;
	private Debouncer<String> debouncer = new Debouncer<>();
	private FrameSeparator frameSeparator = new FrameSeparator(FrameCategorization.METHOD, false);
	private JSON json = new JSON();
	private static final String HTML_PAGE;

	static {
		// from: https://cdn.jsdelivr.net/gh/spiermar/d3-flame-graph@2.0.3/dist/d3-flamegraph.css
		String cssD3Flamegraph = "jslibs/d3-flamegraph.css";
		// from: https://oss.maxcdn.com/html5shiv/3.7.2/html5shiv.min.js
		String jsHtml5shiv = "jslibs/html5shiv.min.js";
		// from: https://oss.maxcdn.com/respond/1.4.2/respond.min.js
		String jsRespond = "jslibs/respond.min.js";
		// from: https://d3js.org/d3.v4.min.js
		String jsD3V4 = "jslibs/d3.v4.min.js";
		// from: https://cdnjs.cloudflare.com/ajax/libs/d3-tip/0.9.1/d3-tip.min.js
		String jsD3Tip = "jslibs/d3-tip.min.js";
		// from: https://cdn.jsdelivr.net/gh/spiermar/d3-flame-graph@3.1.1/dist/d3-flamegraph.min.js
		String jsD3FlameGraph = "jslibs/d3-flamegraph-v3.min.js";
		// jmc flameview coloring, tooltip and other  functions
//		String jsFlameviewName = "flameview.js";
		String cssFlameview = "flameview.css";

		String jsIeLibraries = loadLibraries(jsHtml5shiv, jsRespond);
		String jsD3Libraries = loadLibraries(jsD3V4, jsD3Tip, jsD3FlameGraph);
		String styleheets = loadLibraries(cssD3Flamegraph, cssFlameview);
//		String jsFlameviewColoring = fileContent(jsFlameviewName);

//		String magnifierIcon = getIconBase64(ImageConstants.ICON_MAGNIFIER);

		// formatter arguments for the template: %1 - CSSs stylesheets, %2 - IE9 specific scripts,
		// %3 - Search Icon Base64, %4 - 3rd party scripts, %5 - Flameview Coloring,
		HTML_PAGE = String.format(fileContent("page.template"), styleheets, jsIeLibraries, jsD3Libraries);
		System.out.println(HTML_PAGE);
	}

	@Override
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		super.init(site, memento);
		getSite().getPage().addSelectionListener(this);
	}

	@Override
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			IItemCollection items = getItems((IStructuredSelection) selection);
			System.out.println(Instant.now() + " Queueing");
			debouncer.execute(() -> {
				// check null here so we cancel anything in flight
				if (items == null) {
					return "";
				}
				System.out.println(Instant.now() + " Executing");
				StacktraceTreeModel model = new StacktraceTreeModel(frameSeparator, items, null);
				List<String> types = new ArrayList<>();
				items.forEach(iterable -> {
					types.add(iterable.getType().getName());
				});

				updateBrowser(types, model);
				return "";
			}, 100);
		}
	}

	private void updateBrowser(List<String> types, StacktraceTreeModel model) {
		String d3JSON = model.toD3JSON();
		String typesJSON = json.marshal(types);
		System.out.println(Instant.now() + " Rendering");
		System.out.println(d3JSON);
		System.out.println(typesJSON);
		DisplayToolkit.inDisplayThread().execute(() -> {
			browser.execute(String.format("updateBody('%s', '%s')", typesJSON, d3JSON));
		});
	}

	@Override
	public void setFocus() {
		browser.setFocus();
	}

	@Override
	public void createPartControl(Composite parent) {
		container = new SashForm(parent, SWT.HORIZONTAL);
		browser = new Browser(container, SWT.NONE);
		container.setMaximizedControl(browser);
		browser.addMenuDetectListener(new MenuDetectListener() {
			@Override
			public void menuDetected(MenuDetectEvent e) {
				e.doit = false;
			}
		});
		browser.setText(HTML_PAGE);
	}

	private static String fileContent(String fileName) {
		try {
			return StringToolkit.readString(FlameGraphView.class.getClassLoader().getResourceAsStream(fileName));
		} catch (IOException e) {
			FlightRecorderUI.getDefault().getLogger().log(Level.WARNING,
					MessageFormat.format("Could not load script \"{0}\",\"{1}\"", fileName, e.getMessage())); //$NON-NLS-1$
			return "";
		}
	}

	private static IItemCollection getItems(IStructuredSelection selection) {
		Object first = selection.getFirstElement();
		return AdapterUtil.getAdapter(first, IItemCollection.class);
	}

	private static String loadLibraries(String ... libs) {
		if (libs == null || libs.length == 0) {
			return "";
		} else {
			return Stream.of(libs).map(FlameGraphView::fileContent).collect(Collectors.joining("\n"));
		}
	}
}
