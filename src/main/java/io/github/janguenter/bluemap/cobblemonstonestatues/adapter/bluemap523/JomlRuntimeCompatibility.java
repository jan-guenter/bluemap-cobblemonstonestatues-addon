/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.adapter.bluemap523;

import org.joml.Matrix4f;

/** Exact host JOML runtime identity used by the frozen replay. */
public final class JomlRuntimeCompatibility {

    public static final String JOML_VERSION = "1.10.5";

    private JomlRuntimeCompatibility() {
    }


    public static boolean matchesCurrent() {
        Package runtimePackage = Matrix4f.class.getPackage();
        return JOML_VERSION.equals(runtimePackage.getImplementationVersion())
                && "JOML".equals(runtimePackage.getImplementationTitle());
    }
}
