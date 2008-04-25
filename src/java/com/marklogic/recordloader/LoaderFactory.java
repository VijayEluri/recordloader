/**
 * Copyright (c) 2006-2008 Mark Logic Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * The use of the Apache License does not indicate that this project is
 * affiliated with the Apache Software Foundation.
 */
package com.marklogic.recordloader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.CharsetDecoder;

import org.xmlpull.v1.XmlPullParserException;

import com.marklogic.ps.SimpleLogger;

/**
 * @author Michael Blakeley, michael.blakeley@marklogic.com
 * 
 */
public class LoaderFactory {

    private Monitor monitor;

    private CharsetDecoder decoder;

    private Configuration config;

    private long count = 0;

    private SimpleLogger logger;

    private Constructor<? extends LoaderInterface> loaderConstructor;

    /**
     * @param _monitor
     * @param _decoder
     * @param _config
     * @throws NoSuchMethodException
     * @throws SecurityException
     * @throws ClassNotFoundException
     */
    public LoaderFactory(Monitor _monitor, CharsetDecoder _decoder,
            Configuration _config) throws SecurityException,
            NoSuchMethodException, ClassNotFoundException {
        monitor = _monitor;
        decoder = _decoder;
        config = _config;

        logger = config.getLogger();

        // this should only be called once, in a single-threaded context
        String loaderClassName = config.getLoaderClassName();
        logger.info("Loader is " + loaderClassName);
        Class<? extends LoaderInterface> loaderClass = Class
                .forName(loaderClassName, true,
                        ClassLoader.getSystemClassLoader()).asSubclass(
                        LoaderInterface.class);
        loaderConstructor = loaderClass.getConstructor(new Class[] {});
    }

    private LoaderInterface getLoader() throws LoaderException {
        // if multiple connString are available, we round-robin
        int x = (int) (count++ % config.getConnectionStrings().length);
        try {
            LoaderInterface loader = loaderConstructor.newInstance();
            loader.setConfiguration(config);
            loader.setMonitor(monitor);
            loader.setConnectionUri(config.getConnectionStrings()[x]);
            return loader;
        } catch (IllegalArgumentException e) {
            throw new LoaderException(e);
        } catch (InstantiationException e) {
            throw new LoaderException(e);
        } catch (IllegalAccessException e) {
            throw new LoaderException(e);
        } catch (InvocationTargetException e) {
            throw new LoaderException(e);
        }
    }

    /**
     * @param _in
     * @return
     * @throws LoaderException
     */
    public LoaderInterface newLoader(InputStream _in)
            throws LoaderException {
        return newLoader(_in, null, null);
    }

    /**
     * @param stream
     * @param _name
     * @return
     * @throws LoaderException
     */
    public LoaderInterface newLoader(InputStream stream, String _name,
            String _path) throws LoaderException {
        LoaderInterface loader = getLoader();
        BufferedReader br = new BufferedReader(new InputStreamReader(
                stream, decoder));
        loader.setInput(br);
        setup(loader, _name, _path);
        return loader;
    }

    /**
     * @param _file
     * @return
     * @throws LoaderException
     * @throws XmlPullParserException
     * @throws FileNotFoundException
     */
    public LoaderInterface newLoader(File _file) throws LoaderException {
        // some duplicate code: we want to defer opening the file,
        // to limit the number of open file descriptors
        LoaderInterface loader = getLoader();
        loader.setInput(_file);
        setup(loader, _file.getName(), _file.getPath());
        return loader;
    }

    /**
     * @param loader
     * @param name
     * @param path
     * @throws LoaderException
     */
    private void setup(LoaderInterface loader, String name, String path)
            throws LoaderException {
        if (null != name) {
            loader.setFileBasename(name);
        }
        loader.setRecordPath(path);
    }

}
