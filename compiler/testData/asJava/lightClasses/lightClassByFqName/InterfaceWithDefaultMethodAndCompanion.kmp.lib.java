public abstract interface Test /* Test*/ {
  @org.jetbrains.annotations.NotNull()
  public static final @org.jetbrains.annotations.NotNull() Test.Companion Companion;

  public static final int y;

  public abstract void bar();//  bar()

  public static final class Companion /* Test.Companion*/ {
    private static final int x;

    public static final int y;

    private  Companion();//  .ctor()

    public final int getX();//  getX()

    public final void foo();//  foo()
  }

  public static final class DefaultImpls /* Test.DefaultImpls*/ {
    public static void bar(@org.jetbrains.annotations.NotNull() @org.jetbrains.annotations.NotNull() Test);//  bar(@org.jetbrains.annotations.NotNull() Test)
  }
}
