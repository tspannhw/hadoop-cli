package com.dstreev.hadoop.yarn.parsers;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.util.*;

/**
 * Created by dstreev on 2016-04-26.
 */
public class QueueParser {

    private List<String> skipAttrList = new LinkedList<String>();


    private ObjectMapper mapper = null;
    private JsonNode rootNode = null;

    public QueueParser(String json) throws IOException {
        mapper = new ObjectMapper();
        rootNode = mapper.readValue(json, JsonNode.class);
        skipAttrList.add("type");
    }

    public Map<String, List<Map<String, Object>>> getQueues(String timestamp) {


        JsonNode schedulerNode = rootNode.get("scheduler");
        JsonNode schedulerInfoNode = schedulerNode.get("schedulerInfo");
        JsonNode queuesNode = schedulerInfoNode.get("queues");

        List<String> queuePath = new LinkedList<String>();
        queuePath.add("root");
        List<Map<String, Object>> queueList = processQueues(timestamp, queuePath, queuesNode);

        List<String> queuePath2 = new LinkedList<String>();
        queuePath2.add("root");
        List<Map<String, Object>> usageList = processUsage(timestamp, queuePath2, queuesNode);

        // Now look for Queue Path and Queues and split them.
        Map<String, List<Map<String, Object>>> rtn = new LinkedHashMap<>();

        for (Map<String, Object> item : queueList) {
            if (item.containsKey("maxApplications")) {
                List<Map<String, Object>> qrList = rtn.get("queues");
                if (qrList == null) {
                    qrList = new LinkedList<Map<String, Object>>();
                    rtn.put("queues", qrList);
                }
                qrList.add(item);
            } else {
                List<Map<String, Object>> qpList = rtn.get("queue_paths");
                if (qpList == null) {
                    qpList = new LinkedList<Map<String, Object>>();
                    rtn.put("queue_paths", qpList);
                }
                qpList.add(item);
            }
        }

        rtn.put("queue_usage", usageList);

        return rtn;
    }

    protected List<Map<String, Object>> processQueues(String timestamp, List<String> queuePath, JsonNode queuesNode) {
        List<Map<String, Object>> rtn = new LinkedList<Map<String, Object>>();

        JsonNode queueArray = queuesNode.get("queue");
        if (queueArray.isArray()) {
            Iterator<JsonNode> qAryIter = queueArray.getElements();
            while (qAryIter.hasNext()) {
                JsonNode queueNode = qAryIter.next();
                String queueName = queueNode.get("queueName").asText();
                Map<String, Object> qRec = new LinkedHashMap<String, Object>();
                qRec.put("reporting_ts", timestamp);
                qRec.put("queue.path", buildPath(queuePath));

                qRec = buildQueueRecord(qRec, new LinkedList<String>(), queueNode);
                rtn.add(qRec);

                if (queueNode.has("queues")) {
                    List<String> iPath = new LinkedList<String>(queuePath);
                    iPath.add(queueName);
                    JsonNode iQueuesNode = queueNode.get("queues");
                    List<Map<String, Object>> iRtn = processQueues(timestamp, iPath, iQueuesNode);
                    rtn.addAll(iRtn);
                }

            }
        } else {
            // Wasn't expecting this.
        }

        return rtn;
    }

    protected List<Map<String, Object>> processUsage(String timestamp, List<String> queuePath, JsonNode queuesNode) {
        List<Map<String, Object>> rtn = new LinkedList<Map<String, Object>>();

        JsonNode queueArray = queuesNode.get("queue");
        if (queueArray.isArray()) {
            Iterator<JsonNode> qAryIter = queueArray.getElements();
            while (qAryIter.hasNext()) {
                JsonNode queueNode = qAryIter.next();
                String queueName = queueNode.get("queueName").asText();
                Map<String, Object> qRec = new LinkedHashMap<String, Object>();
                qRec.put("reporting_ts", timestamp);
                qRec.put("queue.path", buildPath(queuePath));

                if (queueNode.has("users") && !queueNode.get("users").isNull()) {
                    List<Map<String, Object>> recs = buildUsageRecords(qRec, new LinkedList<String>(), queueNode);
                    if (recs.size() > 0)
                        rtn.addAll(recs);
                }

                if (queueNode.has("queues")) {
                    List<String> iPath = new LinkedList<String>(queuePath);
                    iPath.add(queueName);
                    JsonNode iQueuesNode = queueNode.get("queues");
                    List<Map<String, Object>> iRtn = processUsage(timestamp, iPath, iQueuesNode);
                    if (iRtn.size() > 0)
                        rtn.addAll(iRtn);
                }


            }
        } else {
            // Wasn't expecting this.
        }

        return rtn;
    }

    protected List<Map<String, Object>> buildUsageRecords(Map<String, Object> record, List<String> treePath, JsonNode node) {

        List<Map<String, Object>> rtn = new LinkedList<Map<String, Object>>();

        if (node.isContainerNode()) {

            Iterator<Map.Entry<String, JsonNode>> iter = node.getFields();
            while (iter.hasNext()) {
                Map.Entry<String, JsonNode> val = iter.next();

                if (val.getValue().isArray()) {
                    if (val.getKey().equals("user")) {
                        Iterator<JsonNode> userNodeIter = val.getValue().getElements();
                        while (userNodeIter.hasNext()) {
                            JsonNode userNode = userNodeIter.next();
                            Map<String, Object> iRecord = new LinkedHashMap<String, Object>(record);
                            iRecord.put("user.numActiveApplications", userNode.get("numActiveApplications").asText());
                            iRecord.put("user.numPendingApplications", userNode.get("numPendingApplications").asText());
                            iRecord.put("user.username", userNode.get("username").asText());
                            JsonNode resNode = userNode.get("resourcesUsed");
                            iRecord.put("user.resourcesUsed.memory", resNode.get("memory").asText());
                            iRecord.put("user.resourcesUsed.vCores", resNode.get("vCores").asText());

                            rtn.add(iRecord);
                        }
                    }
                } else if (val.getValue().isContainerNode()) {
                    // Skip users.
                    if (!val.getKey().equals("users") && !val.getKey().equals("queues")) {
                        List<String> lclTp = new LinkedList<String>(treePath);
                        lclTp.add(val.getKey());
//                        rtn = buildQueueRecord(rtn, lclTp, val.getValue());
                    } else if (val.getKey().equals("users")) {
                        List<Map<String, Object>> iRtn = buildUsageRecords(record, treePath, val.getValue());
                        rtn.addAll(iRtn);
                    }
                } else {
                    if (!val.getKey().equals("users")) {

                        if (treePath.size() == 0) {
                            if (!skipAttrList.contains(val.getKey())) {
                                record.put(val.getKey(), val.getValue().asText());
                            }
                        } else {
                            if (!skipAttrList.contains(val.getKey())) {
                                List<String> lclTp = new LinkedList<String>(treePath);
                                lclTp.add(val.getKey());
                                record.put(buildPath(lclTp), val.getValue().asText());
                            }
                        }
                    }
                }

            }
        } else {
            // Wasn't expecting this.
        }

        return rtn;
    }

    protected Map<String, Object> buildQueueRecord(Map<String, Object> record, List<String> treePath, JsonNode node) {

        Map<String, Object> rtn = record;

        if (node.isContainerNode()) {
            Iterator<Map.Entry<String, JsonNode>> iter = node.getFields();
            while (iter.hasNext()) {
                Map.Entry<String, JsonNode> val = iter.next();
                if (val.getValue().isArray()) {
                    // Not expecting anything here.

                } else if (val.getValue().isContainerNode()) {
                    // Skip users.
                    if (!val.getKey().equals("users") && !val.getKey().equals("queues")) {
                        List<String> lclTp = new LinkedList<String>(treePath);
                        lclTp.add(val.getKey());
                        rtn = buildQueueRecord(rtn, lclTp, val.getValue());
                    }
                } else {
                    if (!val.getKey().equals("users")) {
                        if (treePath.size() == 0) {
                            rtn.put(val.getKey(), val.getValue().asText());
                        } else {
                            List<String> lclTp = new LinkedList<String>(treePath);
                            lclTp.add(val.getKey());
                            rtn.put(buildPath(lclTp), val.getValue().asText());
                        }
                    }
                }
            }
        } else {
            // Wasn't expecting this.
        }

        return rtn;
    }

    protected String buildPath(List<String> path) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> iter = path.iterator();
        while (iter.hasNext()) {
            sb.append(iter.next());
            if (iter.hasNext())
                sb.append(".");
        }
        return sb.toString();

    }

}
