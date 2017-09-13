/*
 * Copyright 2000-2016 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.data.provider;

import com.vaadin.shared.Range;
import com.vaadin.shared.data.HierarchicalDataCommunicatorConstants;
import com.vaadin.ui.ItemCollapseAllowedProvider;
import elemental.json.Json;
import elemental.json.JsonObject;
import no.uio.ifi.trackfind.frontend.data.TreeNode;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Mapper for hierarchical data.
 * <p>
 * Keeps track of the expanded nodes, and size of of the subtrees for each
 * expanded node.
 * <p>
 * This class is framework internal implementation details, and can be changed /
 * moved at any point. This means that you should not directly use this for
 * anything.
 *
 * @param <T> the data type
 * @param <F> the filter type
 * @author Vaadin Ltd
 * @since 8.1
 */
public class HierarchyMapper<T, F> implements DataGenerator<T> { // perfomance boot: 10s -> less than 1s

    // childMap is only used for finding parents of items and clean up on
    // removing children of expanded nodes.
    private Map<T, Set<T>> childMap = new HashMap<>();

    private final HierarchicalDataProvider<T, F> provider;
    private F filter;
    private List<QuerySortOrder> backEndSorting;
    private Comparator<T> inMemorySorting;
    private ItemCollapseAllowedProvider<T> itemCollapseAllowedProvider = t -> true;

    /**
     * Constructs a new HierarchyMapper.
     *
     * @param provider the hierarchical data provider for this mapper
     */
    public HierarchyMapper(HierarchicalDataProvider<T, F> provider) {
        this.provider = provider;
    }

    /**
     * Returns the size of the currently expanded hierarchy.
     *
     * @return the amount of available data
     */
    public int getTreeSize() {
        return (int) getHierarchy(null).count();
    }

    /**
     * Finds the index of the parent of the item in given target index.
     *
     * @param item the item to get the parent of
     * @return the parent index or a negative value if the parent is not found
     */
    public Integer getParentIndex(T item) {
        // TODO: This can be optimised.
        List<T> flatHierarchy = getHierarchy(null).collect(Collectors.toList());
        return flatHierarchy.indexOf(getParentOfItem(item));
    }

    /**
     * Returns whether the given item is expanded.
     *
     * @param item the item to test
     * @return {@code true} if item is expanded; {@code false} if not
     */
    public boolean isExpanded(T item) {
        if (item == null) {
            // Root nodes are always visible.
            return true;
        }
        return ((TreeNode) item).isExpanded();
    }

    /**
     * Expands the given item.
     *
     * @param item     the item to expand
     * @param position the index of item
     * @return range of rows added by expanding the item
     */
    public Range doExpand(T item, Optional<Integer> position) {
        Range rows = Range.withLength(0, 0);
        if (!isExpanded(item) && hasChildren(item)) {
            ((TreeNode) item).setExpanded(true);
            if (position.isPresent()) {
                rows = Range.withLength(position.get() + 1, (int) getHierarchy(item, false).count());
            }
        }
        return rows;
    }

    /**
     * Collapses the given item.
     *
     * @param item     the item to expand
     * @param position the index of item
     * @return range of rows removed by collapsing the item
     */
    public Range doCollapse(T item, Optional<Integer> position) {
        Range removedRows = Range.withLength(0, 0);
        if (isExpanded(item)) {
            if (position.isPresent()) {
                long childCount = getHierarchy(item, false).count();
                removedRows = Range.withLength(position.get() + 1, (int) childCount);
            }
            ((TreeNode) item).setExpanded(false);
        }
        return removedRows;
    }

    @Override
    public void generateData(T item, JsonObject jsonObject) {
        JsonObject hierarchyData = Json.createObject();

        int depth = getDepth(item);
        if (depth >= 0) {
            hierarchyData.put(HierarchicalDataCommunicatorConstants.ROW_DEPTH,
                    depth);
        }

        boolean isLeaf = !getDataProvider().hasChildren(item);
        if (isLeaf) {
            hierarchyData.put(HierarchicalDataCommunicatorConstants.ROW_LEAF,
                    true);
        } else {
            hierarchyData.put(
                    HierarchicalDataCommunicatorConstants.ROW_COLLAPSED,
                    !isExpanded(item));
            hierarchyData.put(HierarchicalDataCommunicatorConstants.ROW_LEAF,
                    false);
            hierarchyData.put(
                    HierarchicalDataCommunicatorConstants.ROW_COLLAPSE_ALLOWED,
                    getItemCollapseAllowedProvider().test(item));
        }

        // add hierarchy information to row as metadata
        jsonObject.put(
                HierarchicalDataCommunicatorConstants.ROW_HIERARCHY_DESCRIPTION,
                hierarchyData);
    }

    /**
     * Gets the current item collapse allowed provider.
     *
     * @return the item collapse allowed provider
     */
    public ItemCollapseAllowedProvider<T> getItemCollapseAllowedProvider() {
        return itemCollapseAllowedProvider;
    }

    /**
     * Sets the current item collapse allowed provider.
     *
     * @param itemCollapseAllowedProvider the item collapse allowed provider
     */
    public void setItemCollapseAllowedProvider(
            ItemCollapseAllowedProvider<T> itemCollapseAllowedProvider) {
        this.itemCollapseAllowedProvider = itemCollapseAllowedProvider;
    }

    /**
     * Gets the current in-memory sorting.
     *
     * @return the in-memory sorting
     */
    public Comparator<T> getInMemorySorting() {
        return inMemorySorting;
    }

    /**
     * Sets the current in-memory sorting. This will cause the hierarchy to be
     * constructed again.
     *
     * @param inMemorySorting the in-memory sorting
     */
    public void setInMemorySorting(Comparator<T> inMemorySorting) {
        this.inMemorySorting = inMemorySorting;
    }

    /**
     * Gets the current back-end sorting.
     *
     * @return the back-end sorting
     */
    public List<QuerySortOrder> getBackEndSorting() {
        return backEndSorting;
    }

    /**
     * Sets the current back-end sorting. This will cause the hierarchy to be
     * constructed again.
     *
     * @param backEndSorting the back-end sorting
     */
    public void setBackEndSorting(List<QuerySortOrder> backEndSorting) {
        this.backEndSorting = backEndSorting;
    }

    /**
     * Gets the current filter.
     *
     * @return the filter
     */
    public F getFilter() {
        return filter;
    }

    /**
     * Sets the current filter. This will cause the hierarchy to be constructed
     * again.
     *
     * @param filter the filter
     */
    public void setFilter(Object filter) {
        this.filter = (F) filter;
    }

    /**
     * Gets the {@code HierarchicalDataProvider} for this
     * {@code HierarchyMapper}.
     *
     * @return the hierarchical data provider
     */
    public HierarchicalDataProvider<T, F> getDataProvider() {
        return provider;
    }

    /**
     * Returns whether given item has children.
     *
     * @param item the node to test
     * @return {@code true} if node has children; {@code false} if not
     */
    public boolean hasChildren(T item) {
        return getDataProvider().hasChildren(item);
    }

    /* Fetch methods. These are used to calculate what to request. */

    /**
     * Gets a stream of items in the form of a flattened hierarchy from the
     * back-end and filter the wanted results from the list.
     *
     * @param range the requested item range
     * @return the stream of items
     */
    public Stream<T> fetchItems(Range range) {
        return getHierarchy(null).skip(range.getStart()).limit(range.length());
    }

    /**
     * Gets a stream of children for the given item in the form of a flattened
     * hierarchy from the back-end and filter the wanted results from the list.
     *
     * @param parent the parent item for the fetch
     * @param range  the requested item range
     * @return the stream of items
     */
    public Stream<T> fetchItems(T parent, Range range) {
        return getHierarchy(parent, false).skip(range.getStart())
                .limit(range.length());
    }

    /* Methods for providing information on the hierarchy. */

    /**
     * Generic method for finding direct children of a given parent, limited by
     * given range.
     *
     * @param parent the parent
     * @param range  the range of direct children to return
     * @return the requested children of the given parent
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Stream<T> doFetchDirectChildren(T parent, Range range) {
        return getDataProvider().fetchChildren(new HierarchicalQuery(
                range.getStart(), range.length(), getBackEndSorting(),
                getInMemorySorting(), getFilter(), parent));
    }

    private int getDepth(T item) {
        int depth = -1;
        while (item != null) {
            item = getParentOfItem(item);
            ++depth;
        }
        return depth;
    }

    private T getParentOfItem(T item) {
        Objects.requireNonNull(item, "Can not find the parent of null");
        return (T) ((TreeNode) item).getParent();
    }

    /**
     * Removes all children of an item identified by a given id. Items removed
     * by this method as well as the original item are all marked to be
     * collapsed.
     *
     * @param id the item id
     */
    private void removeChildren(Object id) {
        // Clean up removed nodes from child map
        Iterator<Entry<T, Set<T>>> iterator = childMap.entrySet().iterator();
        Set<T> invalidatedChildren = new HashSet<>();
        while (iterator.hasNext()) {
            Entry<T, Set<T>> entry = iterator.next();
            T key = entry.getKey();
            if (key != null && getDataProvider().getId(key).equals(id)) {
                invalidatedChildren.addAll(entry.getValue());
                iterator.remove();
            }
        }
        invalidatedChildren.stream().map(getDataProvider()::getId).forEach(this::removeChildren);
    }

    /**
     * Finds the current index of given object. This is based on a search in
     * flattened version of the hierarchy.
     *
     * @param target the target object to find
     * @return optional index of given object
     */
    public Optional<Integer> getIndexOf(T target) {
        if (target == null) {
            return Optional.empty();
        }

        final List<Object> collect = getHierarchy(null).map(provider::getId)
                .collect(Collectors.toList());
        int index = collect.indexOf(getDataProvider().getId(target));
        return Optional.ofNullable(index < 0 ? null : index);
    }

    /**
     * Gets the full hierarchy tree starting from given node.
     *
     * @param parent the parent node to start from
     * @return the flattened hierarchy as a stream
     */
    private Stream<T> getHierarchy(T parent) {
        return getHierarchy(parent, true);
    }

    /**
     * Getst hte full hierarchy tree starting from given node. The starting node
     * can be omitted.
     *
     * @param parent        the parent node to start from
     * @param includeParent {@code true} to include the parent; {@code false} if not
     * @return the flattened hierarchy as a stream
     */
    private Stream<T> getHierarchy(T parent, boolean includeParent) {
        return Stream.of(parent)
                .flatMap(node -> getChildrenStream(node, includeParent));
    }

    /**
     * Gets the stream of direct children for given node.
     *
     * @param parent the parent node
     * @return the stream of direct children
     */
    private Stream<T> getDirectChildren(T parent) {
        return getDataProvider().fetchChildren(new HierarchicalQuery<>(null, parent));
    }

    /**
     * The method to recursively fetch the children of given parent. Used with
     * {@link Stream#flatMap} to expand a stream of parent nodes into a
     * flattened hierarchy.
     *
     * @param parent the parent node
     * @return the stream of all children under the parent, includes the parent
     */
    private Stream<T> getChildrenStream(T parent) {
        return getChildrenStream(parent, true);
    }

    /**
     * The method to recursively fetch the children of given parent. Used with
     * {@link Stream#flatMap} to expand a stream of parent nodes into a
     * flattened hierarchy.
     *
     * @param parent        the parent node
     * @param includeParent {@code true} to include the parent in the stream;
     *                      {@code false} if not
     * @return the stream of all children under the parent
     */
    private Stream<T> getChildrenStream(T parent, boolean includeParent) {
        List<T> childList = Collections.emptyList();
        if (isExpanded(parent)) {
            childList = getDirectChildren(parent).collect(Collectors.toList());
            if (childList.isEmpty()) {
                removeChildren(parent == null ? null
                        : getDataProvider().getId(parent));
            } else {
                childMap.put(parent, new HashSet<>(childList));
            }
        }
        return combineParentAndChildStreams(parent,
                childList.stream().flatMap(this::getChildrenStream),
                includeParent);
    }

    /**
     * Helper method for combining parent and a stream of children into one
     * stream. {@code null} item is never included, and parent can be skipped by
     * providing the correct value for {@code includeParent}.
     *
     * @param parent        the parent node
     * @param children      the stream of children
     * @param includeParent {@code true} to include the parent in the stream;
     *                      {@code false} if not
     * @return the combined stream of parent and its children
     */
    private Stream<T> combineParentAndChildStreams(T parent, Stream<T> children,
                                                   boolean includeParent) {
        boolean parentIncluded = includeParent && parent != null;
        Stream<T> parentStream = parentIncluded ? Stream.of(parent)
                : Stream.empty();
        return Stream.concat(parentStream, children);
    }

    @Override
    public void destroyAllData() {
        childMap.clear();
    }
}