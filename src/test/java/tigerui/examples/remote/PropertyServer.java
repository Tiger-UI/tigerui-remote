/**
 * Copyright 2015 Mike Baum
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package tigerui.examples.remote;

import static tigerui.examples.remote.PropertyCatelog.TEST_ID;

import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import rx.Observable;
import rx.schedulers.Schedulers;
import tigerui.remote.HazelcastPropertyService;
import tigerui.remote.PropertyService;

public class PropertyServer {
    public static void main(String[] args) {
        
        HazelcastInstance hazelcast = Hazelcast.newHazelcastInstance();

        PropertyService service = new HazelcastPropertyService(hazelcast);

            service.registerProperty(TEST_ID, "tacos");
            
            Observable.interval(1, TimeUnit.SECONDS)
                      .observeOn(Schedulers.from(SwingUtilities::invokeLater))
                      .subscribe(tick -> service.setValue(TEST_ID, "" + tick));
    }
}
