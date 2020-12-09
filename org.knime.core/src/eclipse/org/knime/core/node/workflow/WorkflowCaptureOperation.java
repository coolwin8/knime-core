/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Feb 14, 2020 (hornm): created
 */
package org.knime.core.node.workflow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.exec.dataexchange.PortObjectRepository;
import org.knime.core.node.exec.dataexchange.in.PortObjectInNodeModel;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.util.CheckUtils;
import org.knime.core.node.workflow.ConnectionContainer.ConnectionType;
import org.knime.core.node.workflow.NodeID.NodeIDSuffix;
import org.knime.core.node.workflow.action.CollapseIntoMetaNodeResult;
import org.knime.core.node.workflow.capture.WorkflowFragment;
import org.knime.core.node.workflow.capture.WorkflowFragment.Input;
import org.knime.core.node.workflow.capture.WorkflowFragment.Output;
import org.knime.core.node.workflow.capture.WorkflowFragment.PortID;
import org.knime.core.node.workflow.virtual.parchunk.FlowVirtualScopeContext;
import org.knime.core.util.Pair;

/**
 * Operation that allows one to capture parts of a workflow that is encapsulated by {@link CaptureWorkflowStartNode} and
 * {@link CaptureWorkflowEndNode}s.
 *
 * @author Bernd Wiswedel, KNIME AG, Zurich, Switzerland
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 * @since 4.2
 */
public final class WorkflowCaptureOperation {

    /*
     * Name of the component created if there are multiple readers representing multiple ports of the same
     * 'static' input node.
     */
    private static final String READERS_METANODE_NAME = "Readers";

    /*
     * Amount of how much readers a vertically moved relative to each other if there are multiple readers.
     */
    private static final int READERS_VERTICAL_TRANSLATION = 120;

    private WorkflowManager m_wfm;

    private NativeNodeContainer m_startNode;

    private NativeNodeContainer m_endNode;

    WorkflowCaptureOperation(final NodeID endNodeID, final WorkflowManager wfm) throws IllegalScopeException {
        m_endNode = wfm.getNodeContainer(endNodeID, NativeNodeContainer.class, true);
        CheckUtils.checkArgument(m_endNode.getNodeModel() instanceof CaptureWorkflowEndNode,
            "Argument must be instance of %s", CaptureWorkflowEndNode.class.getSimpleName());
        NodeID startNodeID = wfm.getWorkflow().getMatchingScopeStart(endNodeID, CaptureWorkflowStartNode.class,
            CaptureWorkflowEndNode.class);
        if (startNodeID == null) {
            throw new IllegalArgumentException(
                "The passed node id doesn't represent a 'capture workflow end node' or it's respective start node is missing.");
        }
        m_startNode = wfm.getNodeContainer(startNodeID, NativeNodeContainer.class, true);
        m_wfm = wfm;
    }

    /**
     * Carries out the actual capture-operation and returns the captured sub-workflow as a {@link WorkflowFragment}.
     *
     * @return the captured sub-workflow
     */
    public WorkflowFragment capture() {
        WorkflowManager tempParent = null;
        try (WorkflowLock lock = m_wfm.lock()) {
            NodeID endNodeID = m_endNode.getID();
            tempParent = WorkflowManager.EXTRACTED_WORKFLOW_ROOT
                .createAndAddProject("Capture-" + endNodeID, new WorkflowCreationHelper());

            // "scope body" -- will copy those nodes later
            List<NodeContainer> nodesInScope = m_wfm.getWorkflow().getNodesInScope(m_endNode);

            // "scope body" and port object ref readers -- will determine bounding box and move them to the top left
            List<NodeContainer> nodesToDetermineBoundingBox = new ArrayList<>(nodesInScope);

            // copy nodes in scope body
            WorkflowCopyContent.Builder copyContent = WorkflowCopyContent.builder();
            NodeID[] allIDs = nodesInScope.stream().map(NodeContainer::getID).toArray(NodeID[]::new);
            HashSet<NodeID> allIDsHashed = new HashSet<>(Arrays.asList(allIDs));
            //port object reader nodes grouped by the original node
            Map<NodeID, List<NodeID>> portObjectReaderGroups = new HashMap<>();
            Set<NodeIDSuffix> addedPortObjectReaderNodes = new HashSet<>();
            NodeID[] allButScopeIDs = ArrayUtils.removeElements(allIDs, endNodeID, m_startNode.getID());
            copyContent.setNodeIDs(allButScopeIDs);
            copyContent.setIncludeInOutConnections(false);

            final int[] boundingBox = NodeUIInformation.getBoundingBoxOf(nodesToDetermineBoundingBox);
            final int[] moveUIDist = new int[]{-boundingBox[0] + 50, -boundingBox[1] + 50};
            copyContent.setPositionOffset(moveUIDist);
            tempParent.copyFromAndPasteHere(m_wfm, copyContent.build());

            // collect nodes outside the scope body but connected to the scope body (only incoming)
            Map<Pair<NodeID, Integer>, NodeID> visitedSrcPorts = new HashMap<>(); //maps to 'pasted' node id
            boolean isCaptureScopePartOfVirtualScope = getVirtualScopeContext(m_startNode) != null;
            for (int i = 0; i < allButScopeIDs.length; i++) {
                NodeContainer oldNode = m_wfm.getNodeContainer(allButScopeIDs[i]);
                for (int p = 0; p < oldNode.getNrInPorts(); p++) {
                    ConnectionContainer c = m_wfm.getIncomingConnectionFor(allButScopeIDs[i], p);
                    if (c == null) {
                        // ignore: no incoming connection
                    } else if (allIDsHashed.contains(c.getSource())) {
                        // ignore: connection already retained by paste persistor
                    } else {
                        Pair<NodeID, Integer> currentSrcPort = Pair.create(c.getSource(), c.getSourcePort());
                        if (!visitedSrcPorts.containsKey(currentSrcPort)) {
                            // only add portObjectReader if not inserted already in previous loop iteration
                            NodeID sourceID = c.getSource();
                            NodeContainer sourceNode = m_wfm.getNodeContainer(sourceID);
                            NodeUIInformation sourceUIInformation = sourceNode.getUIInformation();
                            nodesToDetermineBoundingBox.add(sourceNode);
                            NodeID pastedID =
                                addPortObjectReferenceReader(m_wfm, tempParent, c, isCaptureScopePartOfVirtualScope);
                            NodeIDSuffix pastedIDSuffix = NodeIDSuffix.create(tempParent.getID(), pastedID);

                            tempParent.getNodeContainer(pastedID).setUIInformation(sourceUIInformation);

                            //TODO deal with WFMIN-connections
                            visitedSrcPorts.put(Pair.create(c.getSource(), c.getSourcePort()), pastedID);
                            addedPortObjectReaderNodes.add(pastedIDSuffix);
                            portObjectReaderGroups.computeIfAbsent(currentSrcPort.getFirst(), id -> new ArrayList<>())
                                .add(pastedID);
                        }

                        // connect all new port object readers to the in-scope-nodes
                        NodeIDSuffix destID = NodeIDSuffix.create(m_wfm.getID(), c.getDest());
                        tempParent.addConnection(visitedSrcPorts.get(currentSrcPort), 1,
                            destID.prependParent(tempParent.getID()), c.getDestPort());
                    }
                }
            }

            // group and position port object readers
            for (List<NodeID> readerGroup : portObjectReaderGroups.values()) {
                for (int i = 0; i < readerGroup.size(); i++) {
                    NodeUIInformation.moveNodeBy(tempParent.getNodeContainer(readerGroup.get(i)),
                        new int[]{moveUIDist[0], moveUIDist[1] + i * READERS_VERTICAL_TRANSLATION});
                }
                if (readerGroup.size() > 1) {
                    int[] boundsFirstNode =
                        tempParent.getNodeContainer(readerGroup.get(0)).getUIInformation().getBounds();

                    //group
                    CollapseIntoMetaNodeResult res =
                        tempParent.collapseIntoMetaNode(readerGroup.toArray(new NodeID[readerGroup.size()]),
                            new WorkflowAnnotation[0], READERS_METANODE_NAME);

                    //update ids
                    WorkflowManager readersMetanode = (WorkflowManager)tempParent.getNodeContainer(res.getCollapsedMetanodeID());
                    for (NodeID id : readerGroup) {
                        NodeIDSuffix suffix = NodeIDSuffix.create(tempParent.getID(), id);
                        addedPortObjectReaderNodes.remove(suffix);
                        addedPortObjectReaderNodes.add(NodeIDSuffix.create(tempParent.getID(),
                            suffix.prependParent(readersMetanode.getID())));
                    }
                    //move component
                    readersMetanode.setUIInformation(NodeUIInformation.builder(readersMetanode.getUIInformation())
                        .setNodeLocation(boundsFirstNode[0], boundsFirstNode[1], boundsFirstNode[2], boundsFirstNode[3])
                        .build());
                }
            }

            //transfer editor settings, too
            tempParent.setEditorUIInformation(m_wfm.getEditorUIInformation());

            List<Input> workflowFragmentInputs = getInputs();
            List<Output> workflowFragmentOutputs = getOutputs();

            return new WorkflowFragment(tempParent, workflowFragmentInputs, workflowFragmentOutputs,
                addedPortObjectReaderNodes);
        } catch (Exception e) {
            if (tempParent != null) {
                WorkflowManager.EXTRACTED_WORKFLOW_ROOT.removeNode(tempParent.getID());
                tempParent = null;
            }
            throw e;
        }
    }

    /**
     * Returns the input of the (to be) captured sub-workflow, i.e. the same ports {@link #capture()} with a
     * subsequent {@link WorkflowFragment#getConnectedInputs()} would return.
     *
     * @return the inputs of the (to be) captured workflow fragment
     */
    public List<Input> getInputs() {
        List<Input> res = new ArrayList<>();
        for (int i = 0; i < m_startNode.getNrOutPorts(); i++) {
            Set<PortID> connections = m_wfm.getOutgoingConnectionsFor(m_startNode.getID(), i).stream().map(cc -> {
                return new PortID(NodeIDSuffix.create(m_wfm.getID(), cc.getDest()), cc.getDestPort());
            }).collect(Collectors.toSet());
            NodeOutPort outPort = m_startNode.getOutPort(i);
            res.add(new Input(outPort.getPortType(), castToDTSpecOrNull(outPort.getPortObjectSpec()), connections));
        }
        return res;
    }

    /**
     * Returns the outputs of the (to be) captured sub-workflow, i.e. the same ports {@link #capture()} with a
     * subsequent {@link WorkflowFragment#getConnectedOutputs()} would return.
     *
     * @return the outputs of the (to be) captured workflow fragment
     */
    public List<Output> getOutputs() {
        List<Output> res = new ArrayList<>();
        for (int i = 0; i < m_endNode.getNrInPorts(); i++) {
            ConnectionContainer cc = m_wfm.getIncomingConnectionFor(m_endNode.getID(), i);
            PortID connectPort = null;
            PortType type = null;
            DataTableSpec spec = null;
            if (cc != null) {
                connectPort = new PortID(NodeIDSuffix.create(m_wfm.getID(), cc.getSource()), cc.getSourcePort());
                NodeOutPort outPort = m_wfm.getNodeContainer(cc.getSource()).getOutPort(cc.getSourcePort());
                type = outPort.getPortType();
                spec = castToDTSpecOrNull(outPort.getPortObjectSpec());
            }
            res.add(new Output(type, spec, connectPort));
        }
        return res;
    }

    private static final DataTableSpec castToDTSpecOrNull(final PortObjectSpec spec) {
        return spec instanceof DataTableSpec ? (DataTableSpec)spec : null;
    }

    /**
     * Helper to add 'port object reference reader nodes' that represent the port objects at the source of connections
     * that lead directly into the scope (i.e. not via the scope start, sort of static inputs).
     *
     * @param srcWfm the original workflow
     * @param newWfm the new workflow fragment to add the reference reader nodes to
     * @param outConn the outgoing connection of the node/port to reference
     * @param isCaptureScopePartOfVirtualScope indicates whether the capture scope is part of a
     *            {@link FlowVirtualScopeContext}
     * @return the id of the port object reference reader node
     */
    private static NodeID addPortObjectReferenceReader(final WorkflowManager srcWfm, final WorkflowManager newWfm,
        final ConnectionContainer outConn, final boolean isCaptureScopePartOfVirtualScope) {
        NodeOutPort upstreamPort;
        int sourcePort = outConn.getSourcePort();
        NodeID sourceID = outConn.getSource();
        NodeContainer sourceNode = srcWfm.getNodeContainer(sourceID);
        if (sourceID.equals(srcWfm.getID())) {
            assert outConn.getType() == ConnectionType.WFMIN;
            upstreamPort = srcWfm.getInPort(sourcePort).getUnderlyingPort();
        } else {
            upstreamPort = sourceNode.getOutPort(sourcePort);
        }
        FlowVirtualScopeContext virtualScopeContext = getVirtualScopeContext(sourceNode);
        if (sourceNode instanceof NativeNodeContainer
            && ((NativeNodeContainer)sourceNode).getNodeModel() instanceof PortObjectInNodeModel) {
            // it's already a reference reader, just copy the node
            WorkflowPersistor copy = srcWfm.copy(WorkflowCopyContent.builder().setNodeIDs(sourceNode.getID()).build());
            return newWfm.paste(copy).getNodeIDs()[0];
        } else if (virtualScopeContext != null && upstreamPort.getPortObject() != null) {
            // the captured nodes are part of a 'virtual scope', i.e. nodes will be deleted after execution
            // -> reference reader node can't reference a node port
            // -> port objects need to be put into the globally available port object repository
            PortObject po = upstreamPort.getPortObject();
            AtomicReference<UUID> id = new AtomicReference<>(PortObjectRepository.getIDFor(po).orElse(null));
            Consumer<Function<ExecutionContext, UUID>> portObjectIDCallback =
                virtualScopeContext.getPortObjectIDCallback();
            if (portObjectIDCallback == null) {
                // fallback in case it's captured within the temporary metanode created by the Parallel Chunk Loop
                // to be fixed with https://knime-com.atlassian.net/browse/AP-15877
                logDataNotAvailableOutsideOfWorkflowWarning(sourceNode);
                return PortObjectRepository.addPortObjectReferenceReaderWithNodeReference(upstreamPort,
                    srcWfm.getProjectWFM().getID(), newWfm, sourceID.getIndex());
            }
            if (id.get() == null) {
                portObjectIDCallback.accept(exec -> {
                    try {
                        id.set(PortObjectRepository.addCopy(po, exec));
                        return id.get();
                    } catch (IOException | CanceledExecutionException ex) {
                        // will be handled in the caller code
                        throw new CompletionException(ex);
                    }
                });
            }
            return PortObjectRepository.addPortObjectReferenceReaderWithRepoReference(upstreamPort, id.get(), newWfm,
                sourceID.getIndex());
        } else {
            if (virtualScopeContext == null && isCaptureScopePartOfVirtualScope) {
                // to be fixed with https://knime-com.atlassian.net/browse/AP-15879
                logDataNotAvailableOutsideOfWorkflowWarning(sourceNode);
            }
            return PortObjectRepository.addPortObjectReferenceReaderWithNodeReference(upstreamPort,
                srcWfm.getProjectWFM().getID(), newWfm, sourceID.getIndex());
        }
    }

    private static void logDataNotAvailableOutsideOfWorkflowWarning(final NodeContainer dataProvidingNode) {
        NodeLogger.getLogger(WorkflowCaptureOperation.class)
            .warn("Data of ports reaching into the capture scope of node '" + dataProvidingNode.getNameWithID()
                + "' will not be available outside of the workflow where it is captured");
    }

    private static FlowVirtualScopeContext getVirtualScopeContext(final NodeContainer nc) {
        FlowVirtualScopeContext context = nc.getFlowObjectStack().peek(FlowVirtualScopeContext.class);
        if (context != null || (nc instanceof WorkflowManager
            && (((WorkflowManager)nc).isProject() || ((WorkflowManager)nc).isComponentProjectWFM()))) {
            // context is given or we are already at the workflow root level
            return context;
        } else {
            // if there is no virtual scope, recursively check the parent(s), too
            return getVirtualScopeContext((NodeContainer)nc.getDirectNCParent());
        }
    }
}
