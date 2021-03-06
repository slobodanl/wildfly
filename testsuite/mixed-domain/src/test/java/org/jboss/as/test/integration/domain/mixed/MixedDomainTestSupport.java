/*
* JBoss, Home of Professional Open Source.
* Copyright 2012, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.as.test.integration.domain.mixed;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.junit.Assert;


/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class MixedDomainTestSupport extends DomainTestSupport {

    public static final String STANDARD_DOMAIN_CONFIG = "copied-master-config/domain.xml";

    private final Version.AsVersion version;
    private final boolean adjustDomain;

    private MixedDomainTestSupport(Version.AsVersion version, String testClass, String domainConfig, String masterConfig, String slaveConfig,
                                   String jbossHome, boolean adjustDomain)
            throws Exception {
        super(testClass, domainConfig, masterConfig, slaveConfig, new WildFlyManagedConfiguration(), new WildFlyManagedConfiguration(jbossHome));
        this.version = version;
        this.adjustDomain = adjustDomain;
    }

    public static MixedDomainTestSupport create(String testClass, Version.AsVersion version) throws Exception {
        return create(testClass, version, STANDARD_DOMAIN_CONFIG, true);
    }

    public static MixedDomainTestSupport create(String testClass, Version.AsVersion version, String domainConfig, boolean adjustDomain) throws Exception {
        final File dir = OldVersionCopier.expandOldVersion(version);
        return new MixedDomainTestSupport(version, testClass, domainConfig, "master-config/host.xml",
                "slave-config/host-slave.xml", dir.getAbsolutePath(), adjustDomain);
    }

    public void start() {
        if (adjustDomain) {
            startAndAdjust();
        } else {
            super.start();
        }
    }

    private void startAndAdjust() {

        try {
            //Start the master in admin only  and reconfigure the domain with what
            //we want to test in the mixed domain and have the DomainAdjuster
            //strip down the domain model to something more workable. The domain
            //adjusters will also make adjustments for the legacy version being
            //tested.
            DomainLifecycleUtil masterUtil = getDomainMasterLifecycleUtil();
            masterUtil.getConfiguration().setAdminOnly(true);
            //masterUtil.getConfiguration().addHostCommandLineProperty("-agentlib:jdwp=transport=dt_socket,address=8787,server=y,suspend=y");
            masterUtil.start();
            DomainAdjuster.adjustForVersion(masterUtil.getDomainClient(), version);

            //Now reload the master in normal mode
            masterUtil.executeAwaitConnectionClosed(Util.createEmptyOperation("reload", PathAddress.pathAddress(HOST, "master")));
            masterUtil.connect();
            masterUtil.awaitHostController(System.currentTimeMillis());

            //Start the slaves
            DomainLifecycleUtil slaveUtil = getDomainSlaveLifecycleUtil();
            if (slaveUtil != null) {
                //slaveUtil.getConfiguration().addHostCommandLineProperty("-agentlib:jdwp=transport=dt_socket,address=8787,server=y,suspend=y");
                slaveUtil.start();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String copyDomainFile() {

        final File originalDomainXml = loadFile("target", "jbossas", "domain", "configuration", "domain.xml");
        final File targetDirectory = createDirectory("target", "test-classes", "copied-master-config");
        final File copiedDomainXml = new File(targetDirectory, "domain.xml");
        if (copiedDomainXml.exists()) {
            Assert.assertTrue(copiedDomainXml.delete());
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(originalDomainXml))) {
            final OutputStream out = new BufferedOutputStream(new FileOutputStream(copiedDomainXml));
            try {
                byte[] bytes = new byte[1024];
                int len = in.read(bytes);
                while (len != -1) {
                    out.write(bytes, 0, len);
                    len = in.read(bytes);
                }
            } finally {
                safeClose(out);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return STANDARD_DOMAIN_CONFIG;
    }

    private static File loadFile(String first, String... parts) {
        final Path p = Paths.get(first, parts);
        final File file = p.toFile();
        Assert.assertTrue(file.getAbsolutePath() + " does not exist", file.exists());
        return file;
    }


    private static File createDirectory(String first, String... parts) {
        Path p = Paths.get(first, parts);
        try {
            File dir = Files.createDirectories(p).toFile();
            Assert.assertTrue(dir.exists());
            return dir;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
