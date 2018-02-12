package com.openkappa.splitmap.models;

import com.openkappa.splitmap.*;
import com.openkappa.splitmap.reduction.DoubleArrayReductionContext;
import org.roaringbitmap.Container;
import org.roaringbitmap.PeekableShortIterator;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import static java.util.stream.Collector.Characteristics.UNORDERED;

public enum Average {
  SUM,
  COUNT;

  public static final int PARAMETER_COUNT = values().length;
  private static final AveragingCollector AVG = new AveragingCollector();

  public static <Model extends Enum<Model>>
  ReductionProcedure<Model, Average, double[], Container> reducer(PrefixIndex<ChunkedDoubleArray> input) {
    ReductionContext<Model, Average, double[]> ctx = new DoubleArrayReductionContext<>(PARAMETER_COUNT, input);
    return ReductionProcedure.mixin(ctx, (key, mask) -> {
      ChunkedDoubleArray x = ctx.readChunk(0, key);
      PeekableShortIterator it = mask.getShortIterator();
      while (it.hasNext()) {
        int i = it.nextAsInt();
        ctx.contributeDouble(SUM, x.get(i), (l, r) -> l + r);
        ctx.contributeDouble(COUNT, 1, (l, r) -> l + r);
      }
    });
  }

  public static <Model extends Enum<Model>>
  Collector<ReductionContext<Model, Average, double[]>, double[], Double> collector() {
    return (AveragingCollector<Model>) AVG;
  }

  private static class AveragingCollector<Model extends Enum<Model>>
          implements Collector<ReductionContext<Model, Average, double[]>, double[], Double> {

    private static Set<Characteristics> CHARACTERISTICS = Set.of(UNORDERED);

    @Override
    public Supplier<double[]> supplier() {
      return () -> new double[PARAMETER_COUNT];
    }

    @Override
    public BiConsumer<double[], ReductionContext<Model, Average, double[]>> accumulator() {
      return (l, r) -> Reduction.sumRightIntoLeft(l, r.getReducedValue());
    }

    @Override
    public BinaryOperator<double[]> combiner() {
      return Reduction::sum;
    }

    @Override
    public Function<double[], Double> finisher() {
      return factors -> {
        double sum = factors[SUM.ordinal()];
        double count = factors[COUNT.ordinal()];
        return sum / count;
      };
    }

    @Override
    public Set<Characteristics> characteristics() {
      return CHARACTERISTICS;
    }
  }
}
