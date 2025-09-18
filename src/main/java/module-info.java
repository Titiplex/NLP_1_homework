/**
 * The module declaration for the "devoir" module.
 * <p>
 * This module specifies the dependencies required for its functionality.
 * It includes the following required modules:
 * - java.datatransfer: Provides interfaces and classes for transferring data between and within applications.
 * - java.desktop: Contains classes for GUI applications and desktop functionalities.
 * - commons.math3: Provides a library for advanced mathematics and statistical operations.
 * - org.jfree.jfreechart: A library for creating professional-quality charts.
 */
module devoir {
    requires java.datatransfer;
    requires java.desktop;
    requires commons.math3;
    requires org.jfree.jfreechart;
}