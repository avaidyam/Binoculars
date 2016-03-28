/*
 * Copyright (c) 2016 Aditya Vaidyam
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.kihara.util;

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
