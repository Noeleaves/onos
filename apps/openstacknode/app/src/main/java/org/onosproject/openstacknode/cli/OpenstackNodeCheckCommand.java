/*
 * Copyright 2016-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.openstacknode.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceService;
import org.onosproject.openstacknode.api.NodeState;
import org.onosproject.openstacknode.api.OpenstackNode;
import org.onosproject.openstacknode.api.OpenstackNodeService;
import org.openstack4j.api.OSClient;

import static org.onosproject.net.AnnotationKeys.PORT_NAME;
import static org.onosproject.openstacknode.api.Constants.GRE_TUNNEL;
import static org.onosproject.openstacknode.api.Constants.VXLAN_TUNNEL;
import static org.onosproject.openstacknode.api.Constants.INTEGRATION_BRIDGE;
import static org.onosproject.openstacknode.api.OpenstackNode.NodeType.CONTROLLER;
import static org.onosproject.openstacknode.api.OpenstackNode.NodeType.GATEWAY;
import static org.onosproject.openstacknode.util.OpenstackNodeUtil.getConnectedClient;

/**
 * Checks detailed node init state.
 */
@Service
@Command(scope = "onos", name = "openstack-node-check",
        description = "Shows detailed node init state")
public class OpenstackNodeCheckCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "hostname", description = "Hostname",
            required = true, multiValued = false)
    @Completion(OpenstackHostnameCompleter.class)
    private String hostname = null;

    private static final String MSG_OK = "OK";
    private static final String MSG_ERROR = "ERROR";

    @Override
    protected void doExecute() {
        OpenstackNodeService osNodeService = get(OpenstackNodeService.class);
        DeviceService deviceService = get(DeviceService.class);

        OpenstackNode osNode = osNodeService.node(hostname);
        if (osNode == null) {
            print("Cannot find %s from registered nodes", hostname);
            return;
        }

        if (osNode.type() == CONTROLLER) {
            print("[Openstack Controller Status]");

            OSClient client = getConnectedClient(osNode);
            if (client == null) {
                error("The given keystone info is incorrect to get authorized to openstack");
                print("keystoneConfig=%s", osNode.keystoneConfig());
            }

            if (osNode.keystoneConfig() != null) {
                print("%s keystoneConfig=%s, neutronConfig=%s",
                        osNode.state() == NodeState.COMPLETE && client != null ?
                                MSG_OK : MSG_ERROR,
                        osNode.keystoneConfig(),
                        osNode.neutronConfig());
            } else {
                print("%s keystoneConfig is missing", MSG_ERROR);
            }
        } else {
            print("[Integration Bridge Status]");
            Device device = deviceService.getDevice(osNode.intgBridge());
            if (device != null) {
                print("%s %s=%s available=%s %s",
                        deviceService.isAvailable(device.id()) ? MSG_OK : MSG_ERROR,
                        INTEGRATION_BRIDGE,
                        device.id(),
                        deviceService.isAvailable(device.id()),
                        device.annotations());
                if (osNode.dataIp() != null) {
                    printPortState(deviceService, osNode.intgBridge(), VXLAN_TUNNEL);
                    printPortState(deviceService, osNode.intgBridge(), GRE_TUNNEL);
                }
                if (osNode.vlanIntf() != null) {
                    printPortState(deviceService, osNode.intgBridge(), osNode.vlanIntf());
                }
                if (osNode.type() == GATEWAY) {
                    printPortState(deviceService, osNode.intgBridge(), osNode.uplinkPort());
                }
            } else {
                print("%s %s=%s is not available",
                        MSG_ERROR,
                        INTEGRATION_BRIDGE,
                        osNode.intgBridge());
            }
        }
    }

    private void printPortState(DeviceService deviceService,
                                DeviceId deviceId, String portName) {
        Port port = deviceService.getPorts(deviceId).stream()
                .filter(p -> p.annotations().value(PORT_NAME).equals(portName) &&
                        p.isEnabled())
                .findAny().orElse(null);

        if (port != null) {
            print("%s %s portNum=%s enabled=%s %s",
                    port.isEnabled() ? MSG_OK : MSG_ERROR,
                    portName,
                    port.number(),
                    port.isEnabled() ? Boolean.TRUE : Boolean.FALSE,
                    port.annotations());
        } else {
            print("%s %s does not exist", MSG_ERROR, portName);
        }
    }
}
