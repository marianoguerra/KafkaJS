package com.gnandoo.kafkajs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KStreamBuilder;
import org.apache.kafka.streams.kstream.KeyValueMapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class KafkaJS {
	
	public static Invocable loadScript(final String path) throws FileNotFoundException, ScriptException {
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
		engine.eval(new FileReader(path));

		return (Invocable) engine;
	}
	
	public static Invocable configureScript(final ObjectNode config) throws FileNotFoundException, ScriptException, NoSuchMethodException {
		final JsonNode scriptPathRaw = config.get("path");
		
		if (!scriptPathRaw.isTextual()) {
			System.err.println("Script: invalid path " + scriptPathRaw);
			return null;
		}
		
		final String scriptPath = scriptPathRaw.asText();
		final Invocable script = loadScript(scriptPath);
		
		return script;
	}
	
	public static Properties objectNodeToProperties(final ObjectNode obj) {
		final Properties props = new Properties();
		final Iterator<Entry<String, JsonNode>> entries = obj.fields();

		while (entries.hasNext()) {
			final Entry<String, JsonNode> entry = entries.next();
			final JsonNode valueRaw = entry.getValue();
			final String key = entry.getKey();
			
			if (valueRaw.isTextual()) {
				props.setProperty(key, valueRaw.asText());
			} else {
				System.err.println("Value for key is not text: " + key);
			}
		}

		return props;
	}

	public static void main(String[] args) throws JsonProcessingException, IOException, NoSuchMethodException, ScriptException, InterruptedException {
		if (args.length != 1) {
			System.err.println("Usage: <program> config.json");
			return;
		}

		final String configPath = args[0];
		final ObjectMapper m = new ObjectMapper();
		final JsonNode config = m.readTree(new File(configPath));
		final JsonNode scriptsRaw = config.get("scripts");
		final JsonNode propsRaw = config.get("props");
		final ArrayNode scripts;
		final ObjectNode propsJson;
		
		if (scriptsRaw != null && scriptsRaw.isArray()) {
			scripts = (ArrayNode)scriptsRaw;
		} else {
			System.err.println("script key is not an array");
			return;
		}
		
		if (propsRaw != null && propsRaw.isObject()) {
			propsJson = (ObjectNode)propsRaw;
		} else {
			System.err.println("props key is not an object");
			return;
		}
		
        final KStreamBuilder builder = new KStreamBuilder();

		
		final int scriptsCount = scripts.size();
		// https://kafka.apache.org/0100/javadoc/org/apache/kafka/streams/StreamsConfig.html
		// https://kafka.apache.org/0100/javadoc/org/apache/kafka/clients/consumer/ConsumerConfig.html
		// https://kafka.apache.org/0100/javadoc/org/apache/kafka/clients/producer/ProducerConfig.html
		final Properties props = objectNodeToProperties(propsJson);
		props.put(StreamsConfig.KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
		
		for (int i = 0; i < scriptsCount; i++) {
			final JsonNode scriptConfigRaw = scripts.get(i);
			
			if (!scriptConfigRaw.isObject()) {
				System.err.println("script config item " + i + " is not an object, ignoring");
				continue;
			}
			
			final ObjectNode scriptConfig = (ObjectNode)scriptConfigRaw;
			final JsonNode inputTopicRaw = scriptConfig.get("inputTopic");
			final JsonNode outputTopicRaw = scriptConfig.get("outputTopic");
			
			if (inputTopicRaw == null || !inputTopicRaw.isTextual()) {
				System.err.println("script " + i + ": inputTopic is not text");
				continue;
			}

			if (outputTopicRaw == null || !outputTopicRaw.isTextual()) {
				System.err.println("script " + i + ": outputTopic is not text");
				continue;
			}
			
			final String inputTopic = inputTopicRaw.asText();
			final String outputTopic = outputTopicRaw.asText();
			final Invocable script = configureScript(scriptConfig);
			
			final KStream<String, String> source = builder.stream(inputTopic);
			final KStream<String, String> processor = source.map(new KeyValueMapper<String, String, KeyValue<String, String>>() {
                @Override
                public KeyValue<String, String> apply(String key, String value) {
					String result;

					try {
						result = (String)script.invokeFunction("onMessage", key, value);
					} catch (NoSuchMethodException e) {
						throw new RuntimeException(e);
					} catch (ScriptException e) {
						throw new RuntimeException(e);
					}

                    return new KeyValue<>(key, result);
                }
            });
			processor.to(Serdes.String(), Serdes.String(), outputTopic);
			System.out.println("script: " + scriptConfig.get("path") + " from: " + inputTopic + " to: " + outputTopic);

		}
		
		final KafkaStreams streams = new KafkaStreams(builder, props);
        streams.start();
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println("Shutdown.");
                streams.close();
            }
        });
        
        while (true) {
        	Thread.sleep(500);
        }
	}

}
