package com.dataviz.ai.service;

import com.dataviz.ai.dto.Nl2SqlDTO;
import com.dataviz.ai.vo.Nl2SqlResultVO;

/**
 * Natural language to SQL translation service
 */
public interface Nl2SqlService {

    /**
     * Translate natural language question to SQL
     */
    Nl2SqlResultVO translate(Nl2SqlDTO dto);

    /**
     * Explain a SQL query in natural language
     */
    Nl2SqlResultVO explain(Nl2SqlDTO dto);
}
