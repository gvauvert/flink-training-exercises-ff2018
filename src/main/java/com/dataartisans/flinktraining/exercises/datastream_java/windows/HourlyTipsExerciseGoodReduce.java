/*
 * Copyright 2018 data Artisans GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dataartisans.flinktraining.exercises.datastream_java.windows;

import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import com.dataartisans.flinktraining.exercises.datastream_java.datatypes.TaxiFare;
import com.dataartisans.flinktraining.exercises.datastream_java.sources.TaxiFareSource;
import com.dataartisans.flinktraining.exercises.datastream_java.utils.ExerciseBase;

/**
 * The "Hourly Tips" exercise of the Flink training
 * (http://training.data-artisans.com).
 * <p>
 * The task of the exercise is to first calculate the total tips collected by each driver, hour by hour, and
 * then from that stream, find the highest tip total in each hour.
 * <p>
 * Parameters:
 * -input path-to-input-file
 */
public class HourlyTipsExerciseGoodReduce extends ExerciseBase {

    public static void main(String[] args) throws Exception {

        // read parameters
        ParameterTool params = ParameterTool.fromArgs(args);
        final String input = params.get("input", ExerciseBase.pathToFareData);

        final int maxEventDelay = 60;       // events are out of order by max 60 seconds
        final int servingSpeedFactor = 600; // events of 10 minutes are served in 1 second

        // set up streaming execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        env.setParallelism(ExerciseBase.parallelism);

        // start the data generator
        DataStream<TaxiFare> fares = env.addSource(fareSourceOrTest(new TaxiFareSource(input, maxEventDelay, servingSpeedFactor)));

        SingleOutputStreamOperator<Tuple3<Long, Long, Float>> hourDriveIdTipSum = fares.
                keyBy(taxiFare -> taxiFare.driverId).
                window(TumblingEventTimeWindows.of(Time.hours(1))).
                reduce((ReduceFunction<TaxiFare>) (value1, value2) -> new TaxiFare(value1.rideId, value1.taxiId, value1.driverId, value1.startTime, value1.paymentType, value1.tip + value2.tip,
                                                                           value1.tolls, value1.totalFare), new ProcessWindowFunction<TaxiFare, Tuple3<Long, Long, Float>, Long, TimeWindow>() {
                    @Override
                    public void process(Long driverId, Context context, Iterable<TaxiFare> elements, Collector<Tuple3<Long, Long, Float>> out)
                            throws Exception {
                        out.collect(Tuple3.of(context.window().getEnd(), driverId, elements.iterator().next().tip));
                    }
                });

        SingleOutputStreamOperator<Tuple3<Long, Long, Float>> hourlyMax = hourDriveIdTipSum.
                windowAll(TumblingEventTimeWindows.of(Time.hours(1))).
                maxBy(2);

        printOrTest(hourlyMax);

        // execute the transformation pipeline
        env.execute("Hourly Tips (java)");
    }
}
