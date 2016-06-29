package edu.princeton.safe.grouping;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import org.opencompare.hac.ClusteringBuilder;
import org.opencompare.hac.HierarchicalAgglomerativeClusterer;
import org.opencompare.hac.agglomeration.AgglomerationMethod;
import org.opencompare.hac.agglomeration.SingleLinkage;
import org.opencompare.hac.experiment.DissimilarityMeasure;
import org.opencompare.hac.experiment.Experiment;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.cursors.IntCursor;

import edu.princeton.safe.AnnotationProvider;
import edu.princeton.safe.GroupingMethod;
import edu.princeton.safe.ProgressReporter;
import edu.princeton.safe.internal.ScoringFunction;
import edu.princeton.safe.io.DomainConsumer;
import edu.princeton.safe.model.CompositeMap;
import edu.princeton.safe.model.EnrichmentLandscape;
import edu.princeton.safe.model.Neighborhood;

public class ClusterBasedGroupingMethod implements GroupingMethod {

    public static final String ID = "cluster";

    double threshold;
    DistanceMethod distanceMethod;

    public ClusterBasedGroupingMethod(double threshold,
                                      DistanceMethod distanceMethod) {
        this.threshold = threshold;
        this.distanceMethod = distanceMethod;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void group(EnrichmentLandscape landscape,
                      CompositeMap compositeMap,
                      int typeIndex,
                      DomainConsumer consumer,
                      ProgressReporter progressReporter) {

        AnnotationProvider annotationProvider = landscape.getAnnotationProvider();
        int totalAttributes = annotationProvider.getAttributeCount();

        IntArrayList filteredIndexes = new IntArrayList();
        for (int i = 0; i < totalAttributes; i++) {
            if (compositeMap.isTop(i, typeIndex)) {
                filteredIndexes.add(i);
            }
        }
        int totalFiltered = filteredIndexes.size();
        progressReporter.setStatus("Top attributes: %d", totalFiltered);

        progressReporter.setStatus("Computing attribute distances...");
        double[][] scores = computeScores(landscape, totalAttributes, filteredIndexes, typeIndex);
        progressReporter.setStatus("Computing dissimilarity matrix...");
        double[] distances = pdist(scores, distanceMethod);

        progressReporter.setStatus("Computing cluster linkages...");
        List<Linkage> linkages = computeLinkages(distances, totalFiltered);
        double height = getHeight(linkages);

        progressReporter.setStatus("Cluster tree height: %f", height);
        progressReporter.setStatus("Total linkages: %d", linkages.size());

        double linkageThreshold = height * threshold;
        int[] parents = computeParents(linkages, totalFiltered, linkageThreshold);
        List<IntArrayList> clusters = computeClusters(parents);

        progressReporter.setStatus("Assigning clusters...");
        // Populate domains with attribute indexes.
        for (IntArrayList cluster : clusters) {
            if (cluster.isEmpty()) {
                continue;
            }
            consumer.startDomain(typeIndex);
            for (IntCursor cursor : cluster) {
                int attributeIndex = filteredIndexes.get(cursor.value);
                consumer.attribute(attributeIndex);
                ;
            }
            consumer.endDomain();
        }
    }

    static List<IntArrayList> computeClusters(int[] parents) {
        // Collect cluster members
        IntObjectMap<IntArrayList> members = new IntObjectHashMap<>();
        for (int i = 0; i < parents.length; i++) {
            int parent = getParent(parents, i);
            if (parent != -1) {
                IntArrayList cluster = members.get(parent);
                if (cluster == null) {
                    cluster = new IntArrayList();
                    members.put(parent, cluster);
                }
                cluster.add(i);
            }
        }

        // Sort clusters by size and assign cluster index by decreasing size.
        return StreamSupport.stream(members.spliterator(), false)
                            .filter(c -> c.value != null)
                            .map(c -> c.value)
                            .sorted((x,
                                     y) -> y.size() - x.size())
                            .collect(Collectors.toList());
    }

    static int[] computeParents(List<Linkage> linkages,
                                int totalAttributes,
                                double threshold) {

        int[] parents = new int[totalAttributes];
        for (int i = 0; i < parents.length; i++) {
            parents[i] = -1;
        }
        ListIterator<Linkage> iterator = linkages.listIterator();
        while (iterator.hasNext()) {
            Linkage linkage = iterator.next();
            // HAC produces some weird linkages where some nodes are
            // self-merged. We should filter these out.
            if (linkage.o1 == linkage.o2 || linkage.dissimilarity >= threshold) {
                continue;
            }
            int parent = Math.min(linkage.o1, linkage.o2);
            parents[linkage.o1] = parent;
            parents[linkage.o2] = parent;
        }
        return parents;
    }

    static double getHeight(List<Linkage> linkages) {
        return linkages.stream()
                       .mapToDouble(l -> Double.isFinite(l.dissimilarity) ? l.dissimilarity : 0)
                       .max()
                       .getAsDouble();
    }

    static int getParent(int[] parents,
                         int index) {
        int parent = parents[index];
        if (parent == index || parent == -1) {
            return parent;
        }

        parent = getParent(parents, parent);
        if (parent != -1) {
            // Update parents as we pop the call stack. This allows future
            // calls to skip traversals that have already been done.
            parents[index] = parent;
        }
        return parent;
    }

    static class Linkage {
        int o1;
        int o2;
        double dissimilarity;

        Linkage(int o1,
                int o2,
                double dissimilarity) {
            this.o1 = o1;
            this.o2 = o2;
            this.dissimilarity = dissimilarity;
        }
    }

    static double[][] computeScores(EnrichmentLandscape result,
                                    int totalAttributes,
                                    IntArrayList attributeIndexes,
                                    int typeIndex) {

        ScoringFunction score = Neighborhood.getScoringFunction(typeIndex);
        List<? extends Neighborhood> neighborhoods = result.getNeighborhoods();
        int filteredAttributes = attributeIndexes.size();
        double[][] scores = new double[filteredAttributes][];
        IntStream.range(0, filteredAttributes)
                 .parallel()
                 .forEach(filteredIndex -> {
                     int attributeIndex = attributeIndexes.get(filteredIndex);
                     scores[filteredIndex] = neighborhoods.stream()
                                                          .mapToDouble(n -> score.get(n, attributeIndex))
                                                          .toArray();
                 });
        return scores;
    }

    static List<Linkage> computeLinkages(double[] condensedDistances,
                                         int totalObservations) {

        Experiment experiment = new Experiment() {
            @Override
            public int getNumberOfObservations() {
                return totalObservations;
            }
        };

        DissimilarityMeasure dissimilarityMeasure = new DissimilarityMeasure() {
            @Override
            public double computeDissimilarity(Experiment experiment,
                                               int i,
                                               int j) {
                int n = experiment.getNumberOfObservations();
                if (i == j) {
                    return 0;
                }
                if (i > j) {
                    return condensedDistances[getIndex(n, j, i)];
                }
                return condensedDistances[getIndex(n, i, j)];
            }
        };

        AgglomerationMethod agglomerationMethod = new SingleLinkage();
        HierarchicalAgglomerativeClusterer clusterer = new HierarchicalAgglomerativeClusterer(experiment,
                                                                                              dissimilarityMeasure,
                                                                                              agglomerationMethod);

        List<Linkage> linkages = new ArrayList<Linkage>();
        ClusteringBuilder builder = new ClusteringBuilder() {
            @Override
            public void merge(int i,
                              int j,
                              double dissimilarity) {
                linkages.add(new Linkage(i, j, dissimilarity));
            }
        };
        clusterer.cluster(builder);
        return linkages;
    }

    static double[] pdist(double[][] distances,
                          DistanceMethod method) {
        int totalRows = distances.length;
        double[] result = new double[totalRows * (totalRows - 1) / 2];

        IntStream.range(0, totalRows)
                 .parallel()
                 .forEach(i -> {
                     for (int j = i + 1; j < totalRows; j++) {
                         int resultIndex = getIndex(totalRows, i, j);
                         double value = method.apply(distances[i], distances[j]);
                         result[resultIndex] = value;
                     }
                 });
        return result;
    }

    static int getIndex(int totalRows,
                        int i,
                        int j) {
        // Assumes upper triangle only, where i < j < totalRows.
        // For i == j, assume -1.
        // For i > j, flip i and j.
        return i * (totalRows * 2 - i - 1) / 2 + j - i - 1;
    }

    static interface ClusterConsumer {

        void startCluster();

        void addMember(int observation);

        void endCluster();
    }

}
