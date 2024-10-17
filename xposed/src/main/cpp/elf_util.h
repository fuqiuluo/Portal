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
#ifndef SANDHOOK_ELF_UTIL_H
#define SANDHOOK_ELF_UTIL_H

#include <string_view>
#include <string>
#include <map>
#include <link.h>

#define SHT_GNU_HASH 0x6ffffff6

namespace SandHook {
    class ElfImg {
    public:
        ElfImg(std::string_view elf);

        template<typename T = void*>
        requires(std::is_pointer_v<T>)
        constexpr const T getSymbolAddress(std::string_view name) const {
            auto offset = getSymbolOffset(name, gnuHash(name), elfHash(name));
            if (offset > 0 && base != nullptr) {
                return reinterpret_cast<T>(static_cast<ElfW(Addr)>((uintptr_t) base + offset - bias));
            }
            return nullptr;
        }

        template<typename T = void*>
        requires(std::is_pointer_v<T>)
        constexpr const T getSymbolAddressByPrefix(std::string_view prefix) const {
            auto offset = prefixLookup(prefix);
            if (offset > 0 && base != nullptr) {
                return reinterpret_cast<T>(static_cast<ElfW(Addr)>((uintptr_t) base + offset - bias));
            }
            return nullptr;
        }

        template<typename T>
        inline constexpr auto offsetOf(ElfW(Ehdr) *head, ElfW(Off) off) const {
            return reinterpret_cast<std::conditional_t<std::is_pointer_v<T>, T, T *>>(reinterpret_cast<uintptr_t>(head) + off);
        }

        constexpr uint32_t elfHash(std::string_view name) const {
            uint32_t h = 0, g;
            for (unsigned char p: name) {
                h = (h << 4) + p;
                g = h & 0xf0000000;
                h ^= g;
                h ^= g >> 24;
            }
            return h;
        }

        constexpr uint32_t gnuHash(std::string_view name) const {
            uint32_t h = 5381;
            for (unsigned char p: name) {
                h += (h << 5) + p;
            }
            return h;
        }

        constexpr inline bool contains(std::string_view a, std::string_view b) const {
            return a.find(b) != std::string_view::npos;
        }

        bool isValid() const {
            return base != nullptr;
        }

        const std::string name() const {
            return elf;
        }

        ~ElfImg();
    private:
        ElfW(Addr) getSymbolOffset(std::string_view name, uint32_t gnuHash, uint32_t elfHash) const;
        ElfW(Addr) elfLookup(std::string_view name, uint32_t hash) const;
        ElfW(Addr) gnuLookup(std::string_view name, uint32_t hash) const;
        ElfW(Addr) linearLookup(std::string_view name) const;
        ElfW(Addr) prefixLookup(std::string_view prefix) const;

        void initLinearMap() const;
        void initModuleBase();

        std::string elf;
        void *base = nullptr;

        off_t size = 0;
        off_t bias = -4396;

        ElfW(Ehdr) *header = nullptr;
        ElfW(Shdr) *sectionHeader = nullptr;
        ElfW(Shdr) *strtab = nullptr;
        ElfW(Shdr) *dynsym = nullptr;

        ElfW(Sym) *symtabStart = nullptr;
        ElfW(Sym) *dynsymStart = nullptr;
        ElfW(Sym) *strtabStart = nullptr;

        ElfW(Off) symtabCount = 0;
        ElfW(Off) symstrOffsetForSymtab = 0;

        uint32_t nbucket_{};
        uint32_t *bucket_ = nullptr;
        uint32_t *chain_ = nullptr;

        uint32_t gnu_nbucket_{};
        uint32_t gnu_symndx_{};
        uint32_t gnu_bloom_size_;
        uint32_t gnu_shift2_;
        uintptr_t *gnu_bloom_filter_;
        uint32_t *gnu_bucket_;
        uint32_t *gnu_chain_;

        mutable std::map<std::string_view, ElfW(Sym) *> symtabs_;
    };
}

#endif //SANDHOOK_ELF_UTIL_H