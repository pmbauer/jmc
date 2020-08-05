package org.openjdk.jmc.flightrecorder.flameview.tree;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.flightrecorder.stacktrace.FrameSeparator;
import org.openjdk.jmc.flightrecorder.stacktrace.StacktraceModel;

public class FlameGraphCalculator {
	// thread pool, execution context
	private static final ExecutorService MODEL_EXECUTOR = Executors.newFixedThreadPool(1);

	private Future<TraceNode> activeFuture;

	public Future<TraceNode> calculate(final IItemCollection items, final FrameSeparator separator) {
		// the caller shouln't be aware of what's running
		// when a new call comes in previous futures are invalidated
		if (activeFuture != null) {
			activeFuture.cancel(true);
		}
		Callable<TraceNode> callable = () -> {
			// check if params are identical to current run
			// if not identical, cancel current run and queue new calculation
			// TODO: how do you check IItemCollections are identical?
			StacktraceModel model = new StacktraceModel(true, separator, items);
			TraceNode root = TraceTreeUtils.createRootWithDescription(items, model.getRootFork().getBranchCount());
			try {
				return TraceTreeUtils.createTree(root, model);
			} catch (InterruptedException e) {
				System.out.println("Interrupted!");
				return null;
			}
		};
		activeFuture = MODEL_EXECUTOR.submit(callable);
		return activeFuture;
	}
}
