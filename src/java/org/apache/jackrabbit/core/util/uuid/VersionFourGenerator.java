/*
 * Copyright 2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.util.uuid;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Random;

/** XXX begin modification by stefan@apache.org */
//import org.apache.commons.id.IdentifierGenerator;
/** XXX end modification by stefan@apache.org */

/**
 * <p>Class is responsible for generating version 4 UUID's per the IETF draft
 * specification. This class attempts to use a java.security.SecureRandom with
 * the following instantiation
 * <code>SecureRandom.getInstance("SHA1PRNG", "SUN")</code>. If neither secure
 * random implementation is avialable or an Exception is raised a java.util.Random
 * is used.</p>
 * <p>Note: Instantiation of SecureRandom is an expensive operation. The
 * constructor therefore creates a static member to hold the SecureRandom.
 * The first call to getInstance may take time; subsequent calls should return
 * quickly.</p>
 *
 * <p>Copied from the Jakarta Commons-Id project</p>
 * <p/>
 * todo remove and use official commons-id release as soon as it is available
 *
 *
 */

/**
 * XXX begin modification by stefan@apache.org
 */
//public final class VersionFourGenerator implements IdentifierGenerator, Constants {
public final class VersionFourGenerator implements Constants {
    /** XXX end modification by stefan@apache.org */

    /**
     * Random used to generate UUID's
     */
    private static final Random regularRandom = new Random();

    /**
     * SecureRandom used to generate UUID's
     */
    private static Random secureRandom;

    /**
     * The pseudo-random number generator to use
     */
    private static String usePRNG = "SHA1PRNG";

    /**
     * The pseudo-random number generator package name to use
     */
    private static String usePRNGPackage = "SUN";

    /**
     * <p>Constructs a new VersionFourGenerator.</p>
     */
    public VersionFourGenerator() {
        super();
    }

    /**
     * <p>Returns a new version four UUID.</p>
     *
     * @return Object a new version 4 UUID.
     */
    public Object nextIdentifier() {
        return nextUUID(false);
    }

    /**
     * <p>Returns a new version four UUID.</p>
     * <p>This overloaded method may produce both UUID's using a <code>SecureRandom</code> as well as using normal
     * <code>Random</code>
     * </p>
     *
     * @param secure indicates whether or not to use <code>SecureRandom</code> in generating the random bits.
     * @return a new version four UUID that was generated by either a <code>Random</code> or <code>SecureRandom</code>.
     */
    public Object nextIdentifier(boolean secure) {
        if (secure) {
            return nextUUID(true);
        }
        return nextUUID(false);
    }

    /**
     * <p>Returns a new version four UUID.</p>
     *
     * @return Object a new version 4 UUID.
     */
    private UUID nextUUID() {
        //Call nextUUID with secure = false
        return nextUUID(false);
    }

    /**
     * <p>Returns a new version four UUID using either <code>SecureRandom</code> or <code>Random</code>.</p>
     *
     * @param secure boolean flag indicating whether to use <code>SecureRandom</code> or <code>Random</code>.
     * @return a new version four UUID using either <code>SecureRandom</code> or <code>Random</code>.
     */
    private UUID nextUUID(boolean secure) {
        byte[] raw = new byte[UUID_BYTE_LENGTH];
        if (secure) {
            //Initialize the secure random if null.
            if (secureRandom == null) {
                try {
                    if (usePRNGPackage != null) {
                        secureRandom = SecureRandom.getInstance(usePRNG, usePRNGPackage);
                    } else {
                        secureRandom = SecureRandom.getInstance(usePRNG);
                    }
                } catch (NoSuchAlgorithmException nsae) {
                    secure = false; //Fail back to default PRNG/Random
                } catch (NoSuchProviderException nspe) {
                    secure = false; //Fail back to default PRNG/Random
                }
                secureRandom.nextBytes(raw);
            }
        }

        if (!secure) {
            regularRandom.nextBytes(raw);
        }

        raw[TIME_HI_AND_VERSION_BYTE_6] &= 0x0F;
        raw[TIME_HI_AND_VERSION_BYTE_6] |= (UUID.VERSION_FOUR << 4);

        raw[CLOCK_SEQ_HI_AND_RESERVED_BYTE_8] &= 0x3F; //0011 1111
        raw[CLOCK_SEQ_HI_AND_RESERVED_BYTE_8] |= 0x80; //1000 0000

        return new UUID(raw);
    }

    /**
     * <p>Allows clients to set the pseudo-random number generator implementation used when generating a version four uuid with
     * the secure option. The secure option uses a <code>SecureRandom</code>. The packageName string may be null to specify
     * no preferred package.</p>
     *
     * @param prngName    the pseudo-random number generator implementation name. For example "SHA1PRNG".
     * @param packageName the package name for the PRNG provider. For example "SUN".
     */
    public static void setPRNGProvider(String prngName, String packageName) {
        VersionFourGenerator.usePRNG = prngName;
        VersionFourGenerator.usePRNGPackage = packageName;
        VersionFourGenerator.secureRandom = null;
    }
}
