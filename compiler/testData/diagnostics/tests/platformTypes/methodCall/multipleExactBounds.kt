// RUN_PIPELINE_TILL: BACKEND
// FIR_IDENTICAL
import java.util.HashMap

interface ModuleDescriptorImpl
interface ModuleInfo
interface ResolverForModule
interface ResolverForProject<M1, R1>

class ResolverForProjectImpl<M : ModuleInfo, R : ResolverForModule>(
        descriptorByModule: Map<M, ModuleDescriptorImpl>,
        delegateResolver: ResolverForProject<M, R>
) : ResolverForProject<M, R>

fun <M2: ModuleInfo, A: ResolverForModule> foo(delegateResolver: ResolverForProject<M2, A>): ResolverForProject<M2, A> {
    val descriptorByModule = HashMap<M2, ModuleDescriptorImpl>()
    return ResolverForProjectImpl(descriptorByModule, delegateResolver)
}

// M = M2
// HashMap<M2, MDI> :< Map<M, MDI> => M = M2!
// R = A
// RFP<M2, A> :< RFP<M, R>

/* GENERATED_FIR_TAGS: classDeclaration, flexibleType, functionDeclaration, interfaceDeclaration, javaFunction,
localProperty, nullableType, primaryConstructor, propertyDeclaration, typeConstraint, typeParameter */
