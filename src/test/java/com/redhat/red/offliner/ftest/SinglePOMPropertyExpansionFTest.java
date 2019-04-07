/**
 * Copyright (C) 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.red.offliner.ftest;

import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.util.Collections;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.junit.Test;

import com.redhat.red.offliner.Main;
import com.redhat.red.offliner.Options;
import com.redhat.red.offliner.ftest.fixture.TestRepositoryServer;

/**
 * Create a one-dependency Maven POM where the version has been replaced by a property. 
 * 
 * Created by dcoleman on 07/04/2019
 */
public class SinglePOMPropertyExpansionFTest extends AbstractOfflinerFunctionalTest {
	
	@Test
	public void run()
		throws Exception
	{
		// We only need one repo server.
		TestRepositoryServer server = newRepositoryServer();
		
		// Generate some test content
		byte[] content = contentGenerator.newBinaryContent(1024);
		
		// Generate a dependency, then replace the version with a property named version.<groupId>
		Dependency expandedDep = contentGenerator.newDependency();
		String version = expandedDep.getVersion();
		String propertyName = "version." + expandedDep.getGroupId();
		
		Dependency dep = expandedDep.clone();
		dep.setVersion("${" + propertyName + "}");
		
		// Create a POM and add the dependency and version property.
		Model pom = contentGenerator.newPom();
		pom.addDependency(dep);
		pom.addProperty(propertyName, version);
		
		String path = contentGenerator.pathOf(expandedDep);
		
		// Register the generated content by writing it to the path within the repo server's dir structure.
        // This way when the path is requested it can be downloaded instead of returning a 404.
		server.registerContent(path, content);
		server.registerContent(path + Main.SHA_SUFFIX, sha1Hex(content));
        server.registerContent(path + Main.MD5_SUFFIX, md5Hex(content));
        
        Model pomDep = contentGenerator.newPomFor(expandedDep);
        String pomPath = contentGenerator.pathOf(pomDep);
        String md5Path = pomPath + Main.MD5_SUFFIX;
        String shaPath = pomPath + Main.SHA_SUFFIX;
        
        String pomStr = contentGenerator.pomToString(pomDep);
        
        server.registerContent(pomPath, pomStr);
        server.registerContent(md5Path, md5Hex(pomStr));
        server.registerContent(shaPath, sha1Hex(pomStr));
        
        // Write the plaintext file we'll use as input.
        File pomFile = temporaryFolder.newFile(getClass().getSimpleName() + ".pom");
        FileUtils.write(pomFile, contentGenerator.pomToString(pom));
        
        Options opts = new Options();
        opts.setBaseUrls(Collections.singletonList(server.getBaseUri()));
        
        // Capture the downloads here so we can verify the content.
        File downloads = temporaryFolder.newFolder();
        
        opts.setDownloads(downloads);
        opts.setLocations(Collections.singletonList(pomFile.getAbsolutePath()));
        
        // run `new Main(opts).run()` and return the Main instance so we can query it for errors, etc.
        Main finishedMain = run(opts);
        
        assertThat( "Wrong number of downloads logged. Should have been 6 (declared jar + its corresponding POM + 2 checksums each).",
                finishedMain.getDownloaded(), equalTo( 6 ) );
        assertThat( "Errors should be empty!", finishedMain.getErrors().isEmpty(), equalTo( true ) );

        File downloaded = new File( downloads, path );
        assertThat( "File: " + path + " doesn't seem to have been downloaded!", downloaded.exists(), equalTo( true ) );
        assertThat( "Downloaded file: " + path + " contains the wrong content!",
                	FileUtils.readFileToByteArray( downloaded ), equalTo( content ) );	
	}

}
