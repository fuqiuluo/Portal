/*
 * This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2019 Swift Gan
 * Copyright (C) 2021 LSPosed Contributors
 */
#include <fstream>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>
#include "elf_util.h"
#include "logging.h"

using namespace SandHook;

ElfImg::ElfImg(std::string_view base_name) : elf(base_name) {
    initModuleBase();
    if (!isValid()) {
        return;
    }

    int fd = open(elf.data(), O_RDONLY);
    if (fd < 0) {
        LOGE("Failed to open %s", elf.data());
        return;
    }

    size = lseek(fd, 0, SEEK_END);
    if (size <= 0) {
        LOGE("lseek() failed for %s", elf.data());
    }

    header = reinterpret_cast<decltype(header)>(mmap(nullptr, size, PROT_READ, MAP_SHARED, fd, 0));
    close(fd);

    sectionHeader = offsetOf<decltype(sectionHeader)>(header, header->e_shoff);
    auto sectionOffset = reinterpret_cast<uintptr_t>(sectionHeader);

    char *section = offsetOf<char *>(header, sectionHeader[header->e_shstrndx].sh_offset);
    for (int i = 0; i < header->e_shnum; i++, sectionOffset += header->e_shentsize) {
        auto *section_h = (ElfW(Shdr) *) sectionOffset;
        char *sectionName = section_h->sh_name + section;

        switch (section_h->sh_type) {
            case SHT_DYNSYM: {
                if (bias == -4396) {
                    ElfW(Off) dynsymOffset = section_h->sh_offset;
                    dynsym = section_h;
                    dynsymStart = offsetOf<decltype(dynsymStart)>(header, dynsymOffset);
                }
                break;
            }

            case SHT_SYMTAB: {
                if (strcmp(sectionName, ".symtab") == 0) {
                    ElfW(Off) symtabSize = section_h->sh_size;
                    ElfW(Off) symtabOffset = section_h->sh_offset;
                    symtabCount = symtabSize / section_h->sh_entsize;
                    symtabStart = offsetOf<decltype(symtabStart)>(header, symtabOffset);
                }
                break;
            }

            case SHT_STRTAB: {
                if (bias == -4396) {
                    ElfW(Off) symstrOffset = section_h->sh_offset;
                    strtab = section_h;
                    strtabStart = offsetOf<decltype(strtabStart)>(header, symstrOffset);
                }

                if (strcmp(sectionName, ".strtab") == 0) {
                    symstrOffsetForSymtab = section_h->sh_offset;
                }
                break;
            }

            case SHT_PROGBITS: {
                if (strtab == nullptr || dynsym == nullptr) {
                    break;
                }

                if (bias == -4396) {
                    bias = (off_t) section_h->sh_addr - (off_t) section_h->sh_offset;
                }
                break;
            }

            case SHT_HASH: {
                auto *d_un = offsetOf<ElfW(Word)>(header, section_h->sh_offset);
                nbucket_ = d_un[0];
                bucket_ = d_un + 2;
                chain_ = bucket_ + nbucket_;
                break;
            }

            case SHT_GNU_HASH: {
                auto *d_buf = reinterpret_cast<ElfW(Word) *>(((size_t) header) + section_h->sh_offset);
                gnu_nbucket_ = d_buf[0];
                gnu_symndx_ = d_buf[1];
                gnu_bloom_size_ = d_buf[2];
                gnu_shift2_ = d_buf[3];
                gnu_bloom_filter_ = reinterpret_cast<decltype(gnu_bloom_filter_)>(d_buf + 4);
                gnu_bucket_ = reinterpret_cast<decltype(gnu_bucket_)>(gnu_bloom_filter_ + gnu_bloom_size_);
                gnu_chain_ = gnu_bucket_ + gnu_nbucket_ - gnu_symndx_;
                break;
            }
        }
    }
}

ElfImg::~ElfImg() {
    if (header) {
        munmap(header, size);
    }
}

ElfW(Addr) ElfImg::elfLookup(std::string_view name, uint32_t hash) const {
    if (nbucket_ == 0) {
        return 0;
    }

    char *strings = (char *) strtabStart;
    for (auto n = bucket_[hash % nbucket_]; n != 0; n = chain_[n]) {
        auto *sym = dynsymStart + n;
        if (name == strings + sym->st_name) {
            return sym->st_value;
        }
    }
    return 0;
}

ElfW(Addr) ElfImg::gnuLookup(std::string_view name, uint32_t hash) const {
    static constexpr auto bloomMaskBits = sizeof(ElfW(Addr)) * 8;
    if (gnu_nbucket_ == 0 || gnu_bloom_size_ == 0) {
        return 0;
    }

    auto bloomWord = gnu_bloom_filter_[(hash / bloomMaskBits) % gnu_bloom_size_];
    uintptr_t mask = 0 | (uintptr_t) 1 << (hash % bloomMaskBits) | (uintptr_t) 1 << ((hash >> gnu_shift2_) % bloomMaskBits);
    if ((mask & bloomWord) == mask) {
        auto symIndex = gnu_bucket_[hash % gnu_nbucket_];
        if (symIndex >= gnu_symndx_) {
            char *strings = (char *) strtabStart;
            do {
                auto *sym = dynsymStart + symIndex;
                if (((gnu_chain_[symIndex] ^ hash) >> 1) == 0
                    && name == strings + sym->st_name) {
                    return sym->st_value;
                }
            } while ((gnu_chain_[symIndex++] & 1) == 0);
        }
    }
    return 0;
}

ElfW(Addr) ElfImg::linearLookup(std::string_view name) const {
    initLinearMap();
    if (auto i = symtabs_.find(name); i != symtabs_.end()) {
        return i->second->st_value;
    }
    return 0;
}

ElfW(Addr) ElfImg::prefixLookup(std::string_view prefix) const {
    initLinearMap();
    if (auto i = symtabs_.lower_bound(prefix); i != symtabs_.end() && i->first.starts_with(prefix)) {
        LOGD("Found prefix %s of %s at offset %p in %s at symtab section by linear lookup", prefix.data(), i->first.data(),
             reinterpret_cast<void *>(i->second->st_value), elf.data());
        return i->second->st_value;
    }
    return 0;
}

ElfW(Addr) ElfImg::getSymbolOffset(std::string_view name, uint32_t gnuHash, uint32_t elfHash) const {
    if (auto offset = gnuLookup(name, gnuHash); offset > 0) {
        LOGD("Found JNI method %s at offset %p in %s at dynsym section by gnu hash", name.data(), reinterpret_cast<void *>(offset), elf.data());
        return offset;
    } else if (offset = elfLookup(name, elfHash); offset > 0) {
        LOGD("Found JNI method %s at offset %p in %s at dynsym section by elf hash", name.data(), reinterpret_cast<void *>(offset), elf.data());
        return offset;
    } else if (offset = linearLookup(name); offset > 0) {
        LOGD("Found JNI method %s at offset %p in %s at symtab section by linear lookup", name.data(), reinterpret_cast<void *>(offset), elf.data());
        return offset;
    }
    return 0;
}

void ElfImg::initLinearMap() const {
    if (symtabs_.empty()) {
        if (symtabStart != nullptr && symstrOffsetForSymtab != 0) {
            for (ElfW(Off) i = 0; i < symtabCount; i++) {
                unsigned int st_type = ELF_ST_TYPE(symtabStart[i].st_info);
                const char *st_name = offsetOf<const char *>(header, symstrOffsetForSymtab + symtabStart[i].st_name);
                if ((st_type == STT_FUNC || st_type == STT_OBJECT) && symtabStart[i].st_size) {
                    symtabs_.emplace(st_name, &symtabStart[i]);
                }
            }
        }
    }
}

void ElfImg::initModuleBase() {
    std::string mapsPath = std::string("/proc/self/maps");
    std::ifstream mapsStream(mapsPath);

    if (base) {
        return;
    }

    if (mapsStream.is_open()) {
        std::string line;
        while (getline(mapsStream, line)){
            if (line.find(elf) != std::string::npos) {
                std::string baseAddress = line.substr(0, line.find('-'));
                std::string elfPath = line.substr(line.find('/'));
                base = reinterpret_cast<void *>(std::stol(baseAddress, nullptr, 16));
                elf = elfPath;
                break;
            }
        }
        mapsStream.close();
    }
}