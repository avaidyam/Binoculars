package com.binoculars.util;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParameterFilter extends Filter {

    @Override
    public String description() {
        return "Parses the URI for GET and POST parameters.";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        parseGetParameters(exchange);
        parsePostParameters(exchange);
        chain.doFilter(exchange);
    }

    private void parseGetParameters(HttpExchange exchange) throws UnsupportedEncodingException {
        Map parameters = new HashMap();
        URI requestedUri = exchange.getRequestURI();
        String query = requestedUri.getRawQuery();

        parseQuery(query, parameters);
        exchange.setAttribute("parameters", parameters);
    }

    private void parsePostParameters(HttpExchange exchange) throws IOException {
        if (!"post".equalsIgnoreCase(exchange.getRequestMethod()))
            return;

        @SuppressWarnings("unchecked")
        Map<String, String> parameters = (Map<String, String>)exchange.getAttribute("parameters");

        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
        String query = new BufferedReader(isr).readLine();
        parseQuery(query, parameters);
    }

    @SuppressWarnings("unchecked")
    private void parseQuery(String query, Map parameters) throws UnsupportedEncodingException {
        if (query == null)
            return;

        String pairs[] = query.split("[&]");
        for (String pair : pairs) {
            String param[] = pair.split("[=]");

            String key = null, value = null;
            if (param.length > 0)
                key = URLDecoder.decode(param[0], System.getProperty("file.encoding"));
            if (param.length > 1)
                value = URLDecoder.decode(param[1], System.getProperty("file.encoding"));

            if (parameters.containsKey(key)) {
                Object obj = parameters.get(key);
                if(obj instanceof List) {
                    List values = (List)obj;
                    values.add(value);
                } else if(obj instanceof String) {
                    List<String> values = new ArrayList<>();
                    values.add((String)obj);
                    values.add(value);
                    parameters.put(key, values);
                }
            } else parameters.put(key, value);
        }
    }
}
