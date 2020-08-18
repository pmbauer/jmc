package org.openjdk.jmc.flightrecorder.flameview.tree;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.flightrecorder.flameview.views.FlameGraphView;
import org.openjdk.jmc.flightrecorder.stacktrace.FrameSeparator;
import org.openjdk.jmc.flightrecorder.stacktrace.StacktraceModel;
import org.openjdk.jmc.flightrecorder.ui.FlightRecorderUI;

public class FlameGraphCalculator {
	// thread pool, execution context
	private static final ExecutorService MODEL_EXECUTOR = Executors.newFixedThreadPool(1);
	private static final Logger LOGGER = FlightRecorderUI.getDefault().getLogger();

	private Future<TraceNode> activeFuture;

	public synchronized Future<TraceNode> calculate(final IItemCollection items, final FrameSeparator separator) {
		LOGGER.log(Level.INFO, ">>> Calculating flame graph in " + Thread.currentThread().getName());
		FlameGraphView.debugIItemCollection(items);
		// the caller shouln't be aware of what's running
		// when a new call comes in previous futures are invalidated
		if (activeFuture != null) {
			LOGGER.log(Level.INFO, ">>> Cancelled existing calculation");
			activeFuture.cancel(true);
		}
		Callable<TraceNode> callable = () -> {
			// check if params are identical to current run
			// if not identical, cancel current run and queue new calculation
			// TODO: how do you check IItemCollections are identical?
			LOGGER.log(Level.INFO, ">>>>> Starting fg calculation");
			StacktraceModel model = new StacktraceModel(true, separator, items);
			TraceNode root = TraceTreeUtils.createRootWithDescription(items, model.getRootFork().getBranchCount());
			try {
				TraceNode result = TraceTreeUtils.createTree(root, model);
				LOGGER.log(Level.INFO, ">>>>> FG calculation complete");
				Thread.sleep(200);
				return result;
			} catch (InterruptedException e) {
				LOGGER.log(Level.INFO, ">>>>> FG calculation interrupted");
				return null;
			}
		};
		activeFuture = MODEL_EXECUTOR.submit(callable);
		LOGGER.log(Level.INFO, ">>> Returning new future");
		return activeFuture;
	}
}
