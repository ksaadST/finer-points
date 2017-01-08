package com.nodestand.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nodestand.nodes.vote.VoteType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ScoreLogger {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private ObjectMapper objectMapper = new ObjectMapper();

    public void logScore(String recipientId, String granterId, String nodeId, int points, VoteType voteType, boolean isNegation) {

        ScoreLog scoreLog = new ScoreLog();
        scoreLog.recipientId = recipientId;
        scoreLog.granterId = granterId;
        scoreLog.nodeId = nodeId;
        scoreLog.points = points;
        scoreLog.voteType = voteType.name();
        scoreLog.isNegation = isNegation;

        try {
            logger.info("{} {}", recipientId, objectMapper.writeValueAsString(scoreLog));
        } catch (JsonProcessingException e) {
            logger.error("Failed to log score!", e);
        }
    }


    public static class ScoreLog {
        public String recipientId; // This is an authorStableId
        public String granterId; // This is a userStableId
        public String nodeId; // This is a nodeStableId
        public Integer points;
        public String voteType;
        public Boolean isNegation;
    }

}
