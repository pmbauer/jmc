package org.openjdk.flightrecorder.stacktrace.tree;

import static org.openjdk.jmc.common.item.ItemToolkit.accessor;
import static org.openjdk.jmc.flightrecorder.JfrAttributes.EVENT_STACKTRACE;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.openjdk.jmc.common.IMCFrame;
import org.openjdk.jmc.common.IMCStackTrace;
import org.openjdk.jmc.common.item.IAttribute;
import org.openjdk.jmc.common.item.IItem;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.item.IItemIterable;
import org.openjdk.jmc.common.item.IMemberAccessor;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.flightrecorder.stacktrace.FrameSeparator;

public class StacktraceTreeModel {
	@SuppressWarnings("deprecation")
	private final static IMemberAccessor<IMCStackTrace, IItem> ACCESSOR_STACKTRACE = accessor(EVENT_STACKTRACE);

	private static final Integer ROOT_ID = null;
	// TODO: Node hashCode uses the method name not the id
	private Map<Integer, Node> nodes = new HashMap<>(1024);
	private Map<Integer, Integer> parentLookup = new HashMap<>(1024);
	private Map<Integer, Set<Integer>> childrenLookup = new HashMap<>(1024);
	private FrameSeparator frameSeparator;
	private IItemCollection items;
	private IAttribute<IQuantity> attribute;

	public Map<Integer, Set<Integer>> getChildrenLookup() {
		return childrenLookup;
	}

	public StacktraceTreeModel(FrameSeparator frameSeparator, IItemCollection items, IAttribute<IQuantity> attribute) {
		this.items = items;
		this.frameSeparator = frameSeparator;
		buildModel();
	}

	void buildModel() {
		childrenLookup.put(ROOT_ID, new HashSet<>());
		for (IItemIterable iterable : items) {
			iterable.forEach((item) -> addItem(item, getAccessor(iterable, attribute)));
		}
	}

	private void addItem(IItem item, IMemberAccessor<IQuantity, IItem> accessor) {
		IMCStackTrace stacktrace = getStackTrace(item);
		if (stacktrace == null) {
			System.out.println("No stacktrace for " + item.getType().getName());
			return;
		}
		List<? extends IMCFrame> frames = getStackTrace(item).getFrames();
		if (frames.isEmpty()) {
			return;
		}

		double value = 0;
		if (accessor != null) {
			value = accessor.getMember(item).doubleValue();
		}

		Integer parentId = ROOT_ID;
		for (int i = frames.size() - 1; i >= 0; i--) {
			AggregatableFrame frame = new AggregatableFrame(frameSeparator, frames.get(i));
			int nodeId = newNodeId(parentId, frame);
			Node current = getOrCreateNode(nodeId, frame);
			current.cumulativeWeight += value;
			current.cumulativeCount += 1;
			if (i == 0) {
				current.weight += value;
				current.count += 1;
			}

			childrenLookup.get(parentId).add(current.getNodeId());
			parentLookup.put(current.getNodeId(), parentId);
			childrenLookup.put(current.getNodeId(), new HashSet<>());
			parentId = current.getNodeId();
		}
	}

	private Node getOrCreateNode(Integer nodeId, AggregatableFrame frame) {
		Node n = nodes.get(nodeId);
		if (n == null) {
			n = new Node(nodeId, frame);
			nodes.put(nodeId, n);
		}
		return n;
	}

	private Integer newNodeId(Integer parentId, AggregatableFrame aframe) {
		// TODO: revisit this addressing scheme 
		if (parentId == null) {
			return aframe.hashCode();
		}
		return Objects.hash(parentId, aframe.hashCode());
	}

	private IMCStackTrace getStackTrace(IItem item) {
		return ACCESSOR_STACKTRACE.getMember(item);
	}

	private static IMemberAccessor<IQuantity, IItem> getAccessor(IItemIterable iterable, IAttribute<IQuantity> attr) {
		IMemberAccessor<IQuantity, IItem> accessor = null;
		if (attr != null) {
			accessor = iterable.getType().getAccessor(attr.getKey());
		}
		return accessor;
	}

	public String toD3JSON() {
		return toD3JSON(null);
	}

	private String toD3JSON(Node node) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		if (node == null) {
			sb.append(JSONProps("root", 0));
		} else {
			sb.append(JSONProps(node.getFrame().getHumanReadableShortString(), node.getCount()));

		}
		Set<Integer> childIds = childrenLookup.get(node != null ? node.getNodeId() : null);
		if (childIds.size() > 0) {
			sb.append(", ").append(addQuotes("children"));
			sb.append(": [");
			for (int childId : childIds) {
				sb.append(toD3JSON(nodes.get(childId)));
				sb.append(", ");
			}
			sb.delete(sb.length() - 2, sb.length());
			sb.append("]");
		}
		sb.append("}");
		return sb.toString();
	}

	private static String JSONProps(String name, double value) {
		StringBuilder sb = new StringBuilder();
		sb.append(addQuotes("name")).append(": ").append(addQuotes(name));
		sb.append(", ");
		sb.append(addQuotes("value")).append(": ").append(value);
		return sb.toString();
	}

	private static String addQuotes(String str) {
		return String.format("\"%s\"", str);
	}
}
