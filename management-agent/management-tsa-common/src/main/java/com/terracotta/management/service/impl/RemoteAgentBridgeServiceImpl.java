/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */
package com.terracotta.management.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terracotta.management.ServiceExecutionException;
import org.terracotta.management.l1bridge.RemoteAgentEndpoint;
import org.terracotta.management.l1bridge.RemoteCallDescriptor;

import com.terracotta.management.service.RemoteAgentBridgeService;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * @author Ludovic Orban
 */
public class RemoteAgentBridgeServiceImpl implements RemoteAgentBridgeService {

  private static final Logger LOG = LoggerFactory.getLogger(RemoteAgentBridgeServiceImpl.class);

  private final MBeanServerConnection mBeanServerConnection = ManagementFactory.getPlatformMBeanServer();

  public RemoteAgentBridgeServiceImpl() {
  }

  @Override
  public Set<String> getRemoteAgentNodeNames() throws ServiceExecutionException {
    try {
      Set<String> nodeNames = new HashSet<String>();
      Set<ObjectName> objectNames = mBeanServerConnection.queryNames(new ObjectName("*:type=" + RemoteAgentEndpoint.IDENTIFIER + ",*"), null);
      if (LOG.isDebugEnabled()) {
        LOG.debug("local server contains {} RemoteAgentEndpoint MBeans", objectNames.size());
        Set<ObjectName> remoteAgentEndpointObjectNames = mBeanServerConnection.queryNames(new ObjectName("*:*"), null);
        LOG.debug("server found {} RemoteAgentEndpoint MBeans", remoteAgentEndpointObjectNames.size());
        for (ObjectName remoteAgentEndpointObjectName : remoteAgentEndpointObjectNames) {
          LOG.debug("  {}", remoteAgentEndpointObjectName);
        }
      }
      for (ObjectName objectName : objectNames) {
        String node = objectName.getKeyProperty("node");
        LOG.debug("RemoteAgentEndpoint node name: {}", node);
        nodeNames.add(node);
      }
      return nodeNames;
    } catch (Exception e) {
      throw new ServiceExecutionException("error making JMX call", e);
    }
  }

  @Override
  public Map<String, String> getRemoteAgentNodeDetails(String nodeName) throws ServiceExecutionException {
    try {
      ObjectName objectName = findRemoteAgentEndpoint(nodeName);

      Map<String, String> attributes = new HashMap<String, String>();
      String version = (String)mBeanServerConnection.getAttribute(objectName, "Version");
      String agency = (String)mBeanServerConnection.getAttribute(objectName, "Agency");
      String[] uuids = (String[])mBeanServerConnection.getAttribute(objectName, "ClientUUIDs");
      String uuidsAsString = stringArrayToString(uuids);
      attributes.put("Version", version);
      attributes.put("Agency", agency);
      attributes.put("ClientUUIDs", uuidsAsString);
      return attributes;
    } catch (ServiceExecutionException see) {
      throw see;
    } catch (Exception e) {
      throw new ServiceExecutionException("error making JMX call", e);
    }
  }

  @Override
  public byte[] invokeRemoteMethod(String nodeName, final RemoteCallDescriptor remoteCallDescriptor) throws ServiceExecutionException {
    try {
      ObjectName objectName = findRemoteAgentEndpoint(nodeName);

      RemoteAgentEndpoint proxy = JMX.newMBeanProxy(mBeanServerConnection, objectName, RemoteAgentEndpoint.class);
      return proxy.invoke(remoteCallDescriptor);
    } catch (ServiceExecutionException see) {
      throw see;
    } catch (Exception e) {
      throw new ServiceExecutionException("Error making remote L1 call", e);
    }
  }

  private ObjectName findRemoteAgentEndpoint(String nodeName) throws IOException, MalformedObjectNameException, ServiceExecutionException {
    Set<ObjectName> objectNames = mBeanServerConnection.queryNames(new ObjectName("*:type=" + RemoteAgentEndpoint.IDENTIFIER + ",*"), null);
    for (ObjectName objectName : objectNames) {
      String node = objectName.getKeyProperty("node");
      if (nodeName.equals(node)) {
        return objectName;
      }
    }
    throw new ServiceExecutionException("Cannot find node : " + nodeName);
  }

  static String stringArrayToString(String[] uuids) {
    String uuidsAsString = "";
    for (String uuid : uuids) {
      if(!uuidsAsString.equals("")) {
        uuidsAsString = uuidsAsString + "," + uuid;
      } else {
        uuidsAsString = uuid;
      }
    }
    return uuidsAsString;
  }

}
