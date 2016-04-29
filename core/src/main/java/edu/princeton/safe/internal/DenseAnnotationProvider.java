package edu.princeton.safe.internal;

import java.io.IOException;

import edu.princeton.safe.AnnotationConsumer;
import edu.princeton.safe.AnnotationParser;
import edu.princeton.safe.IndexedDoubleConsumer;
import edu.princeton.safe.NetworkProvider;

public class DenseAnnotationProvider extends DefaultAnnotationProvider {

    double[][] values;

    public DenseAnnotationProvider(NetworkProvider networkProvider,
                                   AnnotationParser parser)
            throws IOException {

        parser.parse(networkProvider, new AnnotationConsumer() {

            @Override
            public void start(String[] labels,
                              int totalNodes) {
                setAttributeLabes(labels);
                int totalAttributes = labels.length;
                isBinary = true;

                values = new double[totalNodes][];
                for (int i = 0; i < totalNodes; i++) {
                    values[i] = Util.nanArray(totalAttributes);
                }
            }

            @Override
            public void value(int nodeIndex,
                              int attributeIndex,
                              double value) {
                if (nodeIndex != -1) {
                    values[nodeIndex][attributeIndex] = value;
                }
                if (value != 0 && value != 1) {
                    isBinary = false;
                }
                handleAttributeValue(nodeIndex, attributeIndex, value);
            }

            @Override
            public void finish() {
            }
        });
    }

    @Override
    public int getNodeCount() {
        return values.length;
    }

    @Override
    public double getValue(int nodeIndex,
                           int attributeIndex) {
        return values[nodeIndex][attributeIndex];
    }

    @Override
    public void forEachAttributeValue(int attributeIndex,
                                      IndexedDoubleConsumer consumer) {
        for (int i = 0; i < values.length; i++) {
            double value = values[i][attributeIndex];
            if (Double.isNaN(value)) {
                continue;
            }
            consumer.accept(i, value);
        }
    }
}
