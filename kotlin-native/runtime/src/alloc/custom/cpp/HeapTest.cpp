/*
 * Copyright 2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include <algorithm>
#include <cstdint>
#include <random>

#include "CompilerConstants.hpp"
#include "CustomAllocConstants.hpp"
#include "ExtraObjectPage.hpp"
#include "FixedBlockPage.hpp"
#include "GCApi.hpp"
#include "Heap.hpp"
#include "SingleObjectPage.hpp"
#include "gtest/gtest.h"

namespace {

using Heap = typename kotlin::alloc::Heap;
using FixedBlockPage = typename kotlin::alloc::FixedBlockPage;
using NextFitPage = typename kotlin::alloc::NextFitPage;
using SingleObjectPage = typename kotlin::alloc::SingleObjectPage;

inline constexpr int MIN_BLOCK_SIZE = 2;

void mark(void* obj) {
    reinterpret_cast<uint64_t*>(obj)[0] = 1;
}

size_t installType(uint8_t* obj, TypeInfo* typeInfo) {
    auto descriptor = kotlin::alloc::HeapObject::make_descriptor(typeInfo);
    auto& heapObject = *descriptor.construct(obj);
    ObjHeader* object = heapObject.header(descriptor).object();
    object->typeInfoOrMeta_ = const_cast<TypeInfo*>(typeInfo);
    return descriptor.size();
}

TEST(CustomAllocTest, HeapReuseFixedBlockPages) {
    Heap heap(0);
    const int MIN = MIN_BLOCK_SIZE;
    const int MAX = FIXED_BLOCK_PAGE_MAX_BLOCK_SIZE + 1;
    TypeInfo fakeTypes[MAX];
    for (int i = MIN; i < MAX; ++i) {
        fakeTypes[i] = {.typeInfo_ = &fakeTypes[i], .instanceSize_ = 8 * (i - 1), .flags_ = 0};
    }
    FixedBlockPage* pages[MAX];
    kotlin::alloc::FinalizerQueue finalizerQueue;
    for (int blocks = MIN; blocks < MAX; ++blocks) {
        uint32_t bucket = FixedBlockPage::BucketIndex(blocks);
        uint32_t bucketSize = FixedBlockPage::BucketSize(blocks);
        pages[blocks] = heap.GetFixedBlockPage(bucket, bucketSize, finalizerQueue);
        uint8_t* obj = pages[blocks]->TryAllocate(bucketSize);
        size_t size = installType(obj, &fakeTypes[blocks]);
        EXPECT_EQ(size, static_cast<size_t>(blocks * 8));
        mark(obj); // to make the page survive a sweep
    }
    heap.PrepareForGC();
    auto gcHandle = kotlin::gc::GCHandle::createFakeForTests();
    heap.Sweep(gcHandle);
    for (int blocks = MIN; blocks < MAX; ++blocks) {
        uint32_t bucket = FixedBlockPage::BucketIndex(blocks);
        uint32_t bucketSize = FixedBlockPage::BucketSize(blocks);
        EXPECT_EQ(pages[blocks], heap.GetFixedBlockPage(bucket, bucketSize, finalizerQueue));
    }
}

TEST(CustomAllocTest, HeapReuseNextFitPages) {
    Heap heap(0);
    const uint32_t BLOCKSIZE = FIXED_BLOCK_PAGE_MAX_BLOCK_SIZE + 42;
    kotlin::alloc::FinalizerQueue finalizerQueue;
    NextFitPage* page = heap.GetNextFitPage(BLOCKSIZE, finalizerQueue);
    uint8_t* obj = page->TryAllocate(BLOCKSIZE);
    TypeInfo fakeType = {.typeInfo_ = &fakeType, .instanceSize_ = 8 * (BLOCKSIZE - 1), .flags_ = 0};
    size_t size = installType(obj, &fakeType);
    EXPECT_EQ(size, static_cast<size_t>(BLOCKSIZE * 8));
    mark(obj); // to make the page survive a sweep
    heap.PrepareForGC();
    auto gcHandle = kotlin::gc::GCHandle::createFakeForTests();
    heap.Sweep(gcHandle);
    EXPECT_EQ(page, heap.GetNextFitPage(0, finalizerQueue));
}

TEST(CustomAllocTest, HeapFixedBlockPageStartupDelay) {
    Heap heap(kotlin::compiler::fixedBlockStartupDelay());
    for (uint32_t bucket = 0; bucket < FIXED_BLOCK_PAGE_MAX_BUCKET; ++bucket) {
        for (int i = 0 ; i < kotlin::compiler::fixedBlockStartupDelay() ; ++i) {
            EXPECT_TRUE(heap.IsFixedBlockPageBucketDelayed(bucket));
        }
        EXPECT_FALSE(heap.IsFixedBlockPageBucketDelayed(bucket));
    }
}

} // namespace
